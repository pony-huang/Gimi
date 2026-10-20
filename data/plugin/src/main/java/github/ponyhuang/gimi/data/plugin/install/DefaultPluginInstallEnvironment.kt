package github.ponyhuang.gimi.data.plugin.install

import github.ponyhuang.gimi.core.apk.ApkInstaller
import github.ponyhuang.gimi.core.storage.ShareableFileUriFactory
import github.ponyhuang.gimi.domain.plugin.repository.PluginInstallEnvironment
import java.io.File
import javax.inject.Inject

/** [PluginInstallEnvironment] 的默认实现，收敛 PackageManager 与 FileProvider 细节。 */
class DefaultPluginInstallEnvironment @Inject constructor(
    private val apkInstaller: ApkInstaller,
    private val shareableFileUriFactory: ShareableFileUriFactory,
) : PluginInstallEnvironment {

    override fun canRequestPackageInstalls(): Boolean = apkInstaller.canRequestPackageInstalls()

    override fun apkContentUri(apkPath: String): String? = runCatching {
        shareableFileUriFactory.uriFor(File(apkPath)).toString()
    }.getOrNull()
}
