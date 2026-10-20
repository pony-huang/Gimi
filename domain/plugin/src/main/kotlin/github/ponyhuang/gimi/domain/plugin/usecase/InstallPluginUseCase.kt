package github.ponyhuang.gimi.domain.plugin.usecase

import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallResult
import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import github.ponyhuang.gimi.domain.plugin.model.selectPluginAsset
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import github.ponyhuang.gimi.domain.plugin.repository.PluginReleaseRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * 解析最新 Release 中与目录条目对应的 APK，并完成下载与校验。
 *
 * Release 解析失败统一映射为 [PluginInstallFailure.NETWORK]，避免把 HTTP 状态码
 * 或仓库地址暴露到 UI；取消异常始终向上传播。
 */
class InstallPluginUseCase @Inject constructor(
    private val releaseRepository: PluginReleaseRepository,
    private val packageInstaller: PluginPackageInstaller,
) {
    suspend operator fun invoke(
        entry: PluginCatalogEntry,
        onProgress: (Float) -> Unit,
    ): PluginInstallResult {
        val asset = directAssetOrNull(entry) ?: run {
            val release = try {
                releaseRepository.latestRelease()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return PluginInstallResult.Failed(PluginInstallFailure.NETWORK)
            }
            release.selectPluginAsset(entry.releaseAssetPrefix)
                ?: return PluginInstallResult.Failed(PluginInstallFailure.NO_RELEASE_ASSET)
        }
        return try {
            PluginInstallResult.Ready(
                packageInstaller.downloadAndVerify(asset, onProgress),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: PluginPackageInstallException) {
            PluginInstallResult.Failed(e.failure)
        } catch (e: Exception) {
            PluginInstallResult.Failed(PluginInstallFailure.UNKNOWN)
        }
    }

    /** 自定义订阅源可直接给出 APK 直链；非 HTTPS 直链视为无效并回退到 Release 解析。 */
    private fun directAssetOrNull(entry: PluginCatalogEntry): PluginReleaseAsset? {
        val url = entry.apkUrl?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return null
        return PluginReleaseAsset(
            name = url.substringAfterLast('/').ifBlank { "${entry.key}.apk" },
            downloadUrl = url,
            sizeBytes = entry.sizeBytes ?: 0L,
            sha256 = entry.sha256,
        )
    }
}
