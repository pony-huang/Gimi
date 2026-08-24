package github.ponyhuang.gimi.core.network

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** HTTP 文件下载失败，调用方负责映射为所属业务能力的错误。 */
class HttpFileDownloadException(
    val reason: Reason,
) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

/**
 * 业务无关的可取消 HTTP 文件下载引擎。
 * 负责流式写入、进度节流、可选 SHA-256 校验及失败后的半成品清理。
 */
class HttpFileDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun download(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String?,
        destination: File,
        onProgress: (Float) -> Unit,
    ) {
        onProgress(0f)
        val call = okHttpClient.newCall(Request.Builder().url(url).build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                call.cancel()
                destination.delete()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    destination.delete()
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            HttpFileDownloadException(HttpFileDownloadException.Reason.Network),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        writeResponse(
                            response = response,
                            sizeHintBytes = sizeHintBytes,
                            expectedSha256 = expectedSha256,
                            destination = destination,
                            onProgress = onProgress,
                            isActive = { continuation.isActive },
                        )
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (cancelled: CancellationException) {
                        destination.delete()
                        if (continuation.isActive) {
                            continuation.resumeWithException(cancelled)
                        }
                    } catch (error: HttpFileDownloadException) {
                        destination.delete()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } catch (error: Exception) {
                        destination.delete()
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                HttpFileDownloadException(HttpFileDownloadException.Reason.Network),
                            )
                        }
                    }
                }
            })
        }
    }

    private fun writeResponse(
        response: Response,
        sizeHintBytes: Long,
        expectedSha256: String?,
        destination: File,
        onProgress: (Float) -> Unit,
        isActive: () -> Boolean,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        response.use { activeResponse ->
            ensureActive(isActive)
            if (!activeResponse.isSuccessful) {
                throw HttpFileDownloadException(HttpFileDownloadException.Reason.Network)
            }
            val body = activeResponse.body
                ?: throw HttpFileDownloadException(HttpFileDownloadException.Reason.Network)
            val total = body.contentLength().takeIf { it > 0 } ?: sizeHintBytes
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    var lastReportedProgress = 0f
                    var lastReportedAtNanos = System.nanoTime()
                    var hasReportedBytes = false
                    while (true) {
                        ensureActive(isActive)
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                            val nowNanos = System.nanoTime()
                            val shouldReport = !hasReportedBytes ||
                                progress >= 1f ||
                                progress - lastReportedProgress >= MIN_PROGRESS_STEP ||
                                nowNanos - lastReportedAtNanos >= PROGRESS_INTERVAL_NANOS
                            if (shouldReport) {
                                hasReportedBytes = true
                                lastReportedProgress = progress
                                lastReportedAtNanos = nowNanos
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
        }
        ensureActive(isActive)
        if (expectedSha256 != null) {
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha256.equals(expectedSha256, ignoreCase = true)) {
                throw HttpFileDownloadException(HttpFileDownloadException.Reason.ChecksumMismatch)
            }
        }
    }

    private fun ensureActive(isActive: () -> Boolean) {
        if (!isActive()) throw CancellationException("HTTP file download cancelled")
    }

    private companion object {
        private const val MIN_PROGRESS_STEP = 0.01f
        private const val PROGRESS_INTERVAL_NANOS = 250_000_000L
    }
}
