package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.data.voicewake.WakeModelDownloadException.Reason
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class WakeModelDownloadException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

internal class WakeModelDownloader(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun download(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String,
        archive: File,
        onProgress: (Float) -> Unit,
    ) {
        val request = Request.Builder().url(url).build()
        val call = okHttpClient.newCall(request)
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                call.cancel()
                archive.delete()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    archive.delete()
                    if (!continuation.isActive) return
                    continuation.resumeWithException(WakeModelDownloadException(Reason.Network))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        writeResponse(
                            response = response,
                            sizeHintBytes = sizeHintBytes,
                            expectedSha256 = expectedSha256,
                            archive = archive,
                            onProgress = onProgress,
                            isActive = { continuation.isActive },
                        )
                        continuation.resume(Unit)
                    } catch (cancelled: CancellationException) {
                        archive.delete()
                        if (continuation.isActive) {
                            continuation.resumeWithException(cancelled)
                        }
                    } catch (error: WakeModelDownloadException) {
                        archive.delete()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } catch (error: Exception) {
                        archive.delete()
                        if (continuation.isActive) {
                            continuation.resumeWithException(WakeModelDownloadException(Reason.Network))
                        }
                    }
                }
            })
        }
    }

    private fun writeResponse(
        response: Response,
        sizeHintBytes: Long,
        expectedSha256: String,
        archive: File,
        onProgress: (Float) -> Unit,
        isActive: () -> Boolean,
    ) {
        onProgress(0f)
        val digest = MessageDigest.getInstance("SHA-256")
        response.use { activeResponse ->
            ensureActive(isActive)
            with(activeResponse) {
                if (!isSuccessful) throw WakeModelDownloadException(Reason.Network)
                val responseBody = body ?: throw WakeModelDownloadException(Reason.Network)
                val total = responseBody.contentLength().takeIf { it > 0 } ?: sizeHintBytes
                responseBody.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            ensureActive(isActive)
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        ensureActive(isActive)
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha256 != expectedSha256) {
            throw WakeModelDownloadException(Reason.ChecksumMismatch)
        }
    }

    private fun ensureActive(isActive: () -> Boolean) {
        if (!isActive()) throw CancellationException("Wake model download cancelled")
    }
}
