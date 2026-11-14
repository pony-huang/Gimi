package github.ponyhuang.gimi.domain.skills.model

/** 已安装技能的可发现元数据；name 为唯一目录名，description 为用途说明，enabled 表示是否对助手可用。 */
data class InstalledSkill(
    val name: String,
    val description: String,
    val enabled: Boolean = true,
)

sealed interface SkillImportSource {
    /**
     * 从 HTTPS 地址获取归档，可从仓库归档中选择一个完整技能目录。
     * @property value 归档下载地址。
     * @property directory ZIP 内的完整相对目录路径；null 使用原有单技能归档结构。
     */
    data class Url(val value: String, val directory: String? = null) : SkillImportSource
    /** 从应用可读取的文档 uri 获取的技能压缩包。 */
    data class LocalDocument(val uri: String) : SkillImportSource

    /** 已传输到应用内存的 ZIP；content 仅供导入消费，不得记录到日志或会话。 */
    data class ArchiveBytes(val content: ByteArray) : SkillImportSource {
        override fun toString(): String = "ArchiveBytes(size=${content.size})"
    }
}

/** 暂存导入的短期票据；id 用于提交或放弃，replacesExisting 表示准备时存在同名技能。 */
data class PreparedSkillImport(
    val id: String,
    val name: String,
    val description: String,
    val replacesExisting: Boolean,
)

class SkillImportFailure(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
    val downloadIssue: DownloadIssue? = null,
    val httpStatus: Int? = null,
) : Exception(message, cause) {
    /** 下载失败的稳定分类，避免把 URL、响应体或底层异常中的凭据带入会话。 */
    enum class DownloadIssue {
        HttpError,
        ConnectionFailed,
        TimedOut,
        TlsFailed,
        InsecureRedirect,
        EmptyResponse,
    }

    enum class Reason {
        InvalidSource,
        DownloadFailed,
        ArchiveTooLarge,
        TooManyEntries,
        InvalidStructure,
        UnsafeArchive,
        InvalidManifest,
        InvalidSkillName,
        PreparedImportNotFound,
        ReplacementNotAllowed,
        StorageFailure,
    }
}
