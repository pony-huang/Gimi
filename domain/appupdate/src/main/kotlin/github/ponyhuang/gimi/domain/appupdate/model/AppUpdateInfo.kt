package github.ponyhuang.gimi.domain.appupdate.model

/** 一个可下载的 APK 安装包。 */
data class ApkAsset(
    /** 文件名，如 Gimi-v0.2.0-arm64-v8a.apk */
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** 来自 GitHub asset digest 的 SHA-256，可能缺失。 */
    val sha256: String?,
)

/** 一次可用更新的信息。 */
data class AppUpdateInfo(
    /** 从 tag 解析出的语义化版本。 */
    val version: AppVersion,
    /** 原始 tag，如 "v0.2.0"。 */
    val tagName: String,
    val title: String,
    /** 更新日志（markdown 原文，按纯文本展示）。 */
    val changelog: String,
    val assets: List<ApkAsset>,
    val publishedAt: String?,
)
