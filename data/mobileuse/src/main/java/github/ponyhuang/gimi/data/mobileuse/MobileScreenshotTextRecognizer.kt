package github.ponyhuang.gimi.data.mobileuse

import android.graphics.BitmapFactory
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 使用随 APK 提供的中文 OCR 模型补充截图文本框，不依赖 GMS 下载或云端请求。 */
class MobileScreenshotTextRecognizer @Inject constructor() {
    internal suspend fun recognize(frame: CapturedMobileFrame): MobileOcrResult {
        val bitmap = BitmapFactory.decodeByteArray(frame.jpeg, 0, frame.jpeg.size)
            ?: return MobileOcrResult("ocr_invalid_image")
        var client: TextRecognizer? = null
        var handedToTask = false
        return try {
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            client = recognizer
            val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
            // 超时只取消等待；SDK 仍可能读取 Bitmap，必须等实际处理结束后才释放它。
            task.addOnCompleteListener { bitmap.recycle(); recognizer.close() }
            handedToTask = true
            val result = withTimeoutOrNull(2_000) {
                suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                    task.addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                    task.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
                    task.addOnCanceledListener { continuation.cancel() }
                }
            } ?: return MobileOcrResult("ocr_timeout")
            val lines = result.textBlocks.flatMap { it.lines }
            val elements = lines.mapIndexedNotNull { index, line ->
                val bounds = line.boundingBox ?: return@mapIndexedNotNull null
                ocrElement("o$index", line.text,
                    MobileBounds(bounds.left, bounds.top, bounds.right, bounds.bottom), frame.width, frame.height)
            }
            MobileOcrResult("ocr_available", elements.take(200), elements.size > 200)
        } catch (_: MlKitException) {
            MobileOcrResult("ocr_unavailable")
        } catch (_: IllegalStateException) {
            MobileOcrResult("ocr_unavailable")
        } finally {
            if (!handedToTask) { bitmap.recycle(); client?.close() }
        }
    }
}

/** OCR 的局部成功或失败；失败不影响原始截图和原生节点返回。 */
internal data class MobileOcrResult(
    val status: String, val elements: List<MobileElement> = emptyList(), val truncated: Boolean = false,
)

/** 将文字框约束到原始截图坐标，明确只支持坐标 tap 而不是原生 click。 */
internal fun ocrElement(id: String, text: String, bounds: MobileBounds, width: Int, height: Int): MobileElement? {
    val clipped = MobileBounds(bounds.left.coerceIn(0, width), bounds.top.coerceIn(0, height),
        bounds.right.coerceIn(0, width), bounds.bottom.coerceIn(0, height))
    if (text.isBlank() || clipped.left >= clipped.right || clipped.top >= clipped.bottom) return null
    return MobileElement(id, "ocr", clipped, text = text.take(500), actions = listOf("tap"))
}
