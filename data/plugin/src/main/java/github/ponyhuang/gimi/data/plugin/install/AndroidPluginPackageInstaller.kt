package github.ponyhuang.gimi.data.plugin.install

import github.ponyhuang.gimi.core.network.HttpFileDownloadException
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.plugin.PluginStorage
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 插件 APK 下载与校验实现。
 *
 * 安全边界：
 * - 只接受 HTTPS 与已知大小上限的资产；
 * - 下载后再次校验文件大小与 SHA-256（若提供）；
 * - 任一校验失败立即删除半成品，避免安装器拿到未校验文件。
 */
@Singleton
class AndroidPluginPackageInstaller @Inject constructor(
    private val fileDownloader: HttpFileDownloader,
    private val storageRegistry: StorageRegistry,
) : PluginPackageInstaller {

    override suspend fun downloadAndVerify(
        asset: PluginReleaseAsset,
        onProgress: (Float) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        if (!asset.downloadUrl.startsWith("https://", ignoreCase = true)) {
            throw PluginPackageInstallException(PluginInstallFailure.NETWORK)
        }
        if (asset.sizeBytes > MAX_APK_BYTES) {
            throw PluginPackageInstallException(PluginInstallFailure.PACKAGE_TOO_LARGE)
        }
        val directory = runCatching {
            storageRegistry.resolve(PluginStorage.PACKAGES_ID, create = true)
        }.getOrElse {
            throw PluginPackageInstallException(PluginInstallFailure.STORAGE)
        }
        // 每次安装前清掉上一轮 APK，目录只保留当前待安装文件。
        runCatching { directory.listFiles()?.forEach { it.delete() } }
        val destination = File(directory, safeFileName(asset.name))
        try {
            fileDownloader.download(
                url = asset.downloadUrl,
                sizeHintBytes = asset.sizeBytes,
                expectedSha256 = asset.sha256,
                destination = destination,
                onProgress = onProgress,
            )
        } catch (e: HttpFileDownloadException) {
            destination.delete()
            val failure = when (e.reason) {
                HttpFileDownloadException.Reason.Network -> PluginInstallFailure.NETWORK
                HttpFileDownloadException.Reason.ChecksumMismatch -> PluginInstallFailure.CHECKSUM_MISMATCH
            }
            throw PluginPackageInstallException(failure)
        } catch (e: Exception) {
            destination.delete()
            throw PluginPackageInstallException(PluginInstallFailure.UNKNOWN)
        }
        if (!destination.isFile || destination.length() <= 0L) {
            destination.delete()
            throw PluginPackageInstallException(PluginInstallFailure.STORAGE)
        }
        if (destination.length() > MAX_APK_BYTES) {
            destination.delete()
            throw PluginPackageInstallException(PluginInstallFailure.PACKAGE_TOO_LARGE)
        }
        destination.absolutePath
    }

    /** 资产名来自远端，限制字符集与长度，避免路径穿越或超长文件名。 */
    private fun safeFileName(name: String): String {
        val sanitized = name
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(MAX_FILE_NAME_LENGTH)
        return sanitized.ifBlank { "plugin.apk" }.let { fileName ->
            if (fileName.endsWith(".apk", ignoreCase = true)) fileName else "$fileName.apk"
        }
    }

    private companion object {
        const val MAX_APK_BYTES = 50L * 1024L * 1024L
        const val MAX_FILE_NAME_LENGTH = 128
    }
}
