package github.ponyhuang.gimi.data.appupdate.apk

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appupdate.repository.AppInstallEnvironment
import java.io.File
import javax.inject.Inject

/**
 * [AppInstallEnvironment] 的默认实现。
 * PackageManager / FileProvider 等 Android 细节收敛于此。
 */
class DefaultAppInstallEnvironment @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppInstallEnvironment {

    override fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun currentVersionName(): String? = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()

    override fun apkContentUri(apkPath: String): String? = runCatching {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(apkPath),
        ).toString()
    }.getOrNull()
}
