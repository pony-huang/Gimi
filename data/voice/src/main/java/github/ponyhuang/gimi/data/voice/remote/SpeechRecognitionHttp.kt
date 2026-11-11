package github.ponyhuang.gimi.data.voice.remote

import github.ponyhuang.gimi.domain.voice.repository.VoiceAuthenticationException
import github.ponyhuang.gimi.domain.voice.repository.VoiceServiceException
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
                    val body = it.body?.string() ?: throw IOException("Empty speech recognition response")
                    if (!it.isSuccessful) {
                        val message =
                            "Speech recognition HTTP ${it.code}" + if (includeErrorBody) ": $body" else ""
                        // 401/403 是密钥问题，与服务端业务错误是两类不同的用户问题。
                        if (it.code == 401 || it.code == 403) throw VoiceAuthenticationException(message)
                        throw VoiceServiceException(message)
                    }
                    body
                }
                if (continuation.isActive) continuation.resume(text)
            } catch (error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    })
}
