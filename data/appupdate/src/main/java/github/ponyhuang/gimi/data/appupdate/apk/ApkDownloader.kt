package github.ponyhuang.gimi.data.appupdate.apk

import github.ponyhuang.gimi.data.appupdate.apk.ApkDownloadException.Reason
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

internal class ApkDownloadException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

/**
 * APK 流式下载器。仿 data/voicewake 的 WakeModelDownloader，
 * 增加进度回调节流（百分比变化 ≥1% 才回调），避免 StateFlow/通知栏高频刷新。
 */
internal class ApkDownloader(
    private val okHttpClient: OkHttpClient,
) {
    fun download(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String?,
        dest: File,
        onProgress: (Float) -> Unit,
    ) {
        onProgress(0f)
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(url).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApkDownloadException(Reason.Network)
                val body = response.body ?: throw ApkDownloadException(Reason.Network)
                val total = body.contentLength().takeIf { it > 0 } ?: sizeHintBytes
                body.byteStream().use { input ->
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        var lastPercent = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val percent = (copied * 100 / total).toInt()
                                if (percent > lastPercent) {
                                    lastPercent = percent
                                    onProgress((percent / 100f).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                }
            }
        } catch (error: ApkDownloadException) {
            dest.delete()
            throw error
        } catch (cancelled: CancellationException) {
            dest.delete()
            throw cancelled
        } catch (error: Exception) {
            dest.delete()
            throw ApkDownloadException(Reason.Network)
        }
        if (expectedSha256 != null) {
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha256.equals(expectedSha256, ignoreCase = true)) {
                dest.delete()
                throw ApkDownloadException(Reason.ChecksumMismatch)
            }
        }
    }
}
