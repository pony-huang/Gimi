package github.ponyhuang.gimi.data.appupdate.apk

import github.ponyhuang.gimi.core.network.HttpFileDownloadException
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.data.appupdate.apk.ApkDownloadException.Reason
import java.io.File

internal class ApkDownloadException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { Network, ChecksumMismatch }
}

/** APK 下载适配器，将通用 HTTP 文件下载错误转换为应用更新能力使用的错误。 */
internal class ApkDownloader(
    private val fileDownloader: HttpFileDownloader,
) {
    suspend fun download(
        url: String,
        sizeHintBytes: Long,
        expectedSha256: String?,
        dest: File,
        onProgress: (Float) -> Unit,
    ) {
        try {
            fileDownloader.download(
                url = url,
                sizeHintBytes = sizeHintBytes,
                expectedSha256 = expectedSha256,
                destination = dest,
                onProgress = onProgress,
            )
        } catch (error: HttpFileDownloadException) {
            val reason = when (error.reason) {
                HttpFileDownloadException.Reason.Network -> Reason.Network
                HttpFileDownloadException.Reason.ChecksumMismatch -> Reason.ChecksumMismatch
            }
            throw ApkDownloadException(reason)
        }
    }
}
