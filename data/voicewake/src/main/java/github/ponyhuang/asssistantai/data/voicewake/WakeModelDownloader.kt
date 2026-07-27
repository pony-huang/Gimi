package github.ponyhuang.asssistantai.data.voicewake

import github.ponyhuang.asssistantai.data.voicewake.WakeModelDownloadException.Reason
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request

internal class WakeModelDownloadException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

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
        } catch (cancelled: CancellationException) {
            archive.delete()
            throw cancelled
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
