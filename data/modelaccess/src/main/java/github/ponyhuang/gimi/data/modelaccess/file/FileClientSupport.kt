package github.ponyhuang.gimi.data.modelaccess.file

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * 两个文件协议客户端共享的工具：序列化配置、HTTP 客户端、厂商错误体提取与
 * multipart 文件名清洗。原 DeepSeek 专有实现中的坑位处理在此泛化为协议族共用。
 */
internal object FileClientSupport {

    /** 宽松反序列化：厂商新增字段不破坏解码（与聊天协议 DTO 的处理一致）。 */
    val json: Json = Json { ignoreUnknownKeys = true }

    /**
     * 文件传输专用客户端。厂商要求上传在 120 秒内完成，读写超时取 150 秒留出余量；
     * 独立于共享 OkHttpClient，避免文件上传被全局超时策略掐断。
     */
    fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(150, TimeUnit.SECONDS)
        .build()

    /** 提取厂商错误体的可展示片段（错误类型/消息，不携带用户文件内容）。 */
    fun errorDetail(body: String): String = runCatching {
        val error = Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
        error?.let { o ->
            listOf("message", "code", "type").firstNotNullOfOrNull { k ->
                (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            }
        }
    }.getOrNull()?.take(300) ?: body.take(200)

    /**
     * 把文件名压成 multipart 头安全的 ASCII 形态：保留 ASCII 字母数字与扩展名，
     * 其余字符替换为下划线；全非 ASCII 时回退 `upload`。
     *
     * 厂商的 multipart 解析对非 ASCII 文件名会直接 400（Parsing multipart form failed）；
     * 展示名不受影响——注册表与消息里用的都是原始 displayName。
     */
    fun asciiFileName(displayName: String): String {
        val dot = displayName.lastIndexOf('.')
        val base = displayName.substring(0, if (dot > 0) dot else displayName.length)
        val ext = if (dot > 0) displayName.substring(dot) else ""
        val safeBase = base.map { ch ->
            if (ch.isLetterOrDigit() && ch.code < 128) ch else '_'
        }.joinToString("").ifEmpty { "upload" }
        // 扩展名整体为 ASCII 时原样保留（含点号）；含非 ASCII 时丢弃扩展名。
        val safeExt = if (ext.all { it.code < 128 && (it.isLetterOrDigit() || it == '.') }) ext else ""
        return safeBase + safeExt
    }

    /** 把响应体解析为原始 JSON 对象（进统一 DTO 的 raw 兜底字段）；解析失败返回 null。 */
    fun rawObject(body: String): JsonObject? =
        runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()

    /** purpose 映射：hint 优先；DeepSeek 按 mime（识别类只收 PDF/Word/PPT）；其余默认 user_data。 */
    fun purposeFor(vendor: String, mimeType: String, purposeHint: String?): String {
        if (!purposeHint.isNullOrBlank()) return purposeHint
        if (vendor == DEEPSEEK_VENDOR && mimeType in LAYOUT_RECOGNITION_MIME_TYPES) {
            return "rich_layout_recognition"
        }
        return DEFAULT_PURPOSE
    }

    private const val DEEPSEEK_VENDOR = "deepseek"
    private const val DEFAULT_PURPOSE = "user_data"

    /**
     * DeepSeek 识别类 purpose 只收 PDF/Word/PPT（及图片）；纯文本/表格/电子书走
     * user_data（厂商转 Markdown）。不在已知清单内的类型默认 user_data。
     */
    private val LAYOUT_RECOGNITION_MIME_TYPES = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )
}

/**
 * 可取消地执行调用并在 OkHttp 回调线程完成响应转换（两个文件协议客户端共用）。
 *
 * 协程取消时立即 `cancel()` 底层连接（不等满 150 秒读写超时），挂起点恢复为取消；
 * [transform] 内的阻塞读取/拷贝发生在回调线程，连接被取消会以 IOException 中断，
 * 由调用方的既有 catch 归类为 NETWORK。
 */
internal suspend fun <T> Call.awaitCancellable(transform: (Response) -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val outcome = runCatching { response.use { transform(it) } }
                if (continuation.isActive) continuation.resumeWith(outcome)
            }
        })
    }
