package github.ponyhuang.gimi.domain.skills.model

/** 已安装技能的可发现元数据；name 为唯一目录名，description 为用途说明。 */
data class InstalledSkill(
    val name: String,
    val description: String,
)

sealed interface SkillImportSource {
    /** 从 HTTPS 地址 value 获取的技能压缩包。 */
    data class Url(val value: String) : SkillImportSource
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
) : Exception(message, cause) {
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
