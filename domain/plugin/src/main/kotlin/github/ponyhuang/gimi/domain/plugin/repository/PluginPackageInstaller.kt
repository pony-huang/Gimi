package github.ponyhuang.gimi.domain.plugin.repository

import github.ponyhuang.gimi.domain.plugin.model.PluginPackageInstallException
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset

/** 下载并校验插件 APK 的契约；Android 与网络细节由 data 层实现。 */
interface PluginPackageInstaller {
    /**
     * 下载 [asset] 到受管缓存目录，校验大小、SHA-256 与 APK 包名后返回本地路径。
     * [versionCode] 非空时必须与归档安装版本完全一致；签名与覆盖安装由系统安装器裁决。
     *
     * @throws PluginPackageInstallException 下载或任一校验失败。
     */
    suspend fun downloadAndVerify(
        asset: PluginReleaseAsset,
        packageName: String,
        versionCode: Long?,
        onProgress: (Float) -> Unit,
    ): String
}
