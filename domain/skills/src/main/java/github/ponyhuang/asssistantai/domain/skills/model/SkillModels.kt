package github.ponyhuang.asssistantai.domain.skills.model

data class InstalledSkill(
    val name: String,
    val description: String,
)

sealed interface SkillImportSource {
    data class Url(val value: String) : SkillImportSource
    data class LocalDocument(val uri: String) : SkillImportSource
}

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
