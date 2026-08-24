package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.core.network.HttpFileDownloadException
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.data.voicewake.WakeModelDownloadException.Reason
import java.io.File

internal class WakeModelDownloadException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

/** 唤醒模型下载适配器，将通用 HTTP 文件下载错误转换为模型安装错误。 */
internal class WakeModelDownloader(
    private val fileDownloader: HttpFileDownloader,
) {
    suspend fun download(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String,
        archive: File,
        onProgress: (Float) -> Unit,
    ) {
        try {
            fileDownloader.download(
                url = url,
                sizeHintBytes = sizeHintBytes,
                expectedSha256 = expectedSha256,
                destination = archive,
                onProgress = onProgress,
            )
        } catch (error: HttpFileDownloadException) {
            val reason = when (error.reason) {
                HttpFileDownloadException.Reason.Network -> Reason.Network
                HttpFileDownloadException.Reason.ChecksumMismatch -> Reason.ChecksumMismatch
            }
            throw WakeModelDownloadException(reason)
        }
    }
}
