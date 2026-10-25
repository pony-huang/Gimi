package github.ponyhuang.gimi.data.plugin.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.network.HttpFileDownloadException
import github.ponyhuang.gimi.core.network.HttpFileDownloader
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.plugin.PluginStorage
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
    @ApplicationContext private val context: Context,
    private val fileDownloader: HttpFileDownloader,
    private val storageRegistry: StorageRegistry,
) : PluginPackageInstaller {

    override suspend fun downloadAndVerify(
        asset: PluginReleaseAsset,
        packageName: String,
        versionCode: Long?,
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
        // 系统安装器可能仍在读取上一份 APK；独立文件避免列表/详情页互相覆盖或删除。
        val expiredBefore = System.currentTimeMillis() - PACKAGE_TTL_MS
        directory.listFiles()?.filter { it.lastModified() < expiredBefore }?.forEach { it.delete() }
        val destination = try {
            File.createTempFile("plugin-", ".apk", directory)
        } catch (e: IOException) {
            throw PluginPackageInstallException(PluginInstallFailure.STORAGE)
        }
        try {
            fileDownloader.download(
                url = asset.downloadUrl,
                sizeHintBytes = asset.sizeBytes,
                expectedSha256 = asset.sha256,
                destination = destination,
                onProgress = onProgress,
            )
        } catch (e: CancellationException) {
            destination.delete()
            throw e
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
        if (asset.sizeBytes > 0L && destination.length() != asset.sizeBytes) {
            destination.delete()
            throw PluginPackageInstallException(PluginInstallFailure.CHECKSUM_MISMATCH)
        }
        val archive = try {
            context.packageManager.getPackageArchiveInfo(destination.absolutePath, 0)
        } catch (e: RuntimeException) {
            null
        }
        if (archive == null || archive.packageName != packageName ||
            (versionCode != null && archive.longVersionCode != versionCode)
        ) {
            destination.delete()
            throw PluginPackageInstallException(PluginInstallFailure.INVALID_PACKAGE)
        }
        destination.absolutePath
    }

    private companion object {
        const val MAX_APK_BYTES = 50L * 1024L * 1024L
        const val PACKAGE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
