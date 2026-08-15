package github.ponyhuang.gimi.data.appupdate.apk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

/** APK 安装相关系统交互：未知来源权限、安装 Intent、签名比对。 */
class ApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canRequestPackageInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 系统「允许安装未知来源」设置页。 */
    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${context.packageName}".toUri(),
    )

    /** 调起系统安装器。 */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 「应用详情」设置页，签名不匹配时引导用户卸载。 */
    fun appDetailsSettingsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri(),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 下载的 APK 与当前应用签名是否一致。
     * CI 无 keystore secret 时 release 回退 debug 签名，与本机安装包可能不同。
     * 无法解析 APK 时按不一致处理，交给系统安装器拦截。
     */
    fun hasSameSignature(apk: File): Boolean {
        val current = signatureDigest(context.packageName, sourceDir = null) ?: return true
        val incoming = signatureDigest(context.packageName, sourceDir = apk.absolutePath)
            ?: return false
        return current.contentEquals(incoming)
    }

    private fun signatureDigest(packageName: String, sourceDir: String?): ByteArray? {
        val info = if (sourceDir == null) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } else {
            context.packageManager.getPackageArchiveInfo(
                sourceDir,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        } ?: return null
        val signatures = info.signingInfo?.apkContentsSigners ?: return null
        val first = signatures.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
    }
}
