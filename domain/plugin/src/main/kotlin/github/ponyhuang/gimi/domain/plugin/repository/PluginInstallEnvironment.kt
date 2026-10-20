package github.ponyhuang.gimi.domain.plugin.repository

/**
 * 安装插件所需的 Android 环境查询。
 *
 * Android-free 契约，由 data 层基于 PackageManager / FileProvider 实现，
 * 让 ViewModel 无需持有 Context 即可判断安装权限并生成安装器 URI。
 */
interface PluginInstallEnvironment {
    /** 系统是否已授予「安装未知来源应用」权限。 */
    fun canRequestPackageInstalls(): Boolean

    /** 已下载 APK 的 FileProvider content Uri（字符串形式）；不可用返回 null。 */
    fun apkContentUri(apkPath: String): String?
}
