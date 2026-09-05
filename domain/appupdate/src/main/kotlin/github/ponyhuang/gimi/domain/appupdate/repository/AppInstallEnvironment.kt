package github.ponyhuang.gimi.domain.appupdate.repository

/**
 * 应用安装环境查询：未知来源安装权限、当前版本名与可安装 Uri。
 *
 * Android-free 契约，由 data 层基于 PackageManager / FileProvider 实现，
 * 让 ViewModel 无需持有 [android.content.Context] 即可完成安装决策。
 */
interface AppInstallEnvironment {

    /** 系统是否已授予「安装未知来源应用」权限。 */
    fun canRequestPackageInstalls(): Boolean

    /** 当前应用版本名；解析失败返回 null。 */
    fun currentVersionName(): String?

    /** 已下载 APK 的 FileProvider content Uri（字符串形式）；不可用返回 null。 */
    fun apkContentUri(apkPath: String): String?
}
