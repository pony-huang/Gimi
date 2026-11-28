package github.ponyhuang.gimi.data.modelaccess.shared

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** 将识别请求的实际 HTTP 生命周期绑定到话轮取消，包括响应体正在读取的阶段。 */
internal suspend fun Call.awaitSpeechRecognitionBody(includeErrorBody: Boolean = false): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val text = response.use {
                    val body = it.body?.string()
                    if (!it.isSuccessful) {
                        val message =
                            "Speech recognition HTTP ${it.code}" + if (includeErrorBody) ": $body" else ""
                        // 401/403 是密钥问题，与服务端业务错误是两类不同的用户问题。
                        throw voiceHttpFailure(it.code, body.orEmpty(), message)
                    }
                    body ?: throw IOException("Empty speech recognition response")
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}
