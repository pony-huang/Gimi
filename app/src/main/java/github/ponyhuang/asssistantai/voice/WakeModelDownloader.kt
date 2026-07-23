package github.ponyhuang.asssistantai.voice

import github.ponyhuang.asssistantai.voice.WakeModelDownloadException.Reason
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/** 下载失败原因，由调用方映射为用户可读的本地化文案。 */
internal class WakeModelDownloadException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

/**
 * 唤醒模型下载器：流式写入磁盘，边下边算 SHA-256，完成后与期望值比对。
 * 独立于 Android Context，便于 JVM 单测（MockWebServer）。
 */
internal class WakeModelDownloader(
    private val okHttpClient: OkHttpClient,
) {
    fun download(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String,
        archive: File,
        onProgress: (Float) -> Unit,
    ) {
        onProgress(0f)
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url(url).build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw WakeModelDownloadException(Reason.Network)
                val body = response.body ?: throw WakeModelDownloadException(Reason.Network)
                val total = body.contentLength().takeIf { it > 0 } ?: sizeHintBytes
                body.byteStream().use { input ->
                    FileOutputStream(archive).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
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
        } catch (error: WakeModelDownloadException) {
            archive.delete()
            throw error
        } catch (error: Exception) {
            archive.delete()
            throw WakeModelDownloadException(Reason.Network)
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha256 != expectedSha256) {
            archive.delete()
            throw WakeModelDownloadException(Reason.ChecksumMismatch)
        }
    }
}
