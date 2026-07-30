package github.ponyhuang.gimi.domain.conversation.model

enum class AttachmentCategory {
    IMAGE,
    AUDIO,
    DOCUMENT;

    companion object {
        fun inferMimeType(displayName: String): String? = when (
            displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        ) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> null
        }

        fun from(mimeType: String?, displayName: String): AttachmentCategory? {
            val normalizedMime = mimeType?.lowercase()?.substringBefore(';')
            val extension = displayName.substringAfterLast('.', "").lowercase()
            return when {
                normalizedMime in IMAGE_MIME_TYPES || extension in IMAGE_EXTENSIONS -> IMAGE
                normalizedMime in AUDIO_MIME_TYPES || extension in AUDIO_EXTENSIONS -> AUDIO
                normalizedMime in DOCUMENT_MIME_TYPES || extension in DOCUMENT_EXTENSIONS -> DOCUMENT
                else -> null
            }
        }
    }
}

val AttachmentCategory.supportedMimeTypes: Set<String>
    get() = when (this) {
        AttachmentCategory.IMAGE -> IMAGE_MIME_TYPES
        AttachmentCategory.AUDIO -> AUDIO_MIME_TYPES
        AttachmentCategory.DOCUMENT -> DOCUMENT_MIME_TYPES
    }

private val IMAGE_MIME_TYPES = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/gif",
)

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

private val AUDIO_MIME_TYPES = setOf(
    "audio/wav",
    "audio/x-wav",
    "audio/mpeg",
    "audio/mp3",
)

private val AUDIO_EXTENSIONS = setOf("wav", "mp3")

private val DOCUMENT_MIME_TYPES = setOf(
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "application/csv",
    "text/tsv",
    "text/x-iif",
    "application/x-iif",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/rtf",
    "text/rtf",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/vnd.ms-powerpoint",
    "application/javascript",
    "application/typescript",
    "application/json",
    "application/yaml",
    "application/x-yaml",
    "application/toml",
    "text/plain",
    "text/markdown",
    "text/xml",
    "text/html",
    "text/css",
    "text/javascript",
    "text/x-kotlin",
    "text/x-java",
    "text/x-python",
    "text/x-c",
    "text/x-c++",
    "text/x-go",
    "text/x-rust",
    "text/x-shellscript",
    "text/x-sh",
    "text/x-bash",
    "text/x-sql",
    "text/calendar",
    "text/vtt",
    "text/srt",
)

private val DOCUMENT_EXTENSIONS = setOf(
    "pdf",
    "xla", "xlb", "xlc", "xlm", "xls", "xlsx", "xlt", "xlw",
    "csv", "tsv", "iif",
    "doc", "docx", "dot", "odt", "rtf",
    "pot", "ppa", "pps", "ppt", "pptx", "pwz", "wiz",
    "asm", "bat", "c", "cc", "conf", "cpp", "css", "cxx", "def", "dic",
    "eml", "h", "hh", "htm", "html", "ics", "ifb", "in", "js", "json",
    "ksh", "list", "log", "markdown", "md", "mht", "mhtml", "mime", "mjs",
    "nws", "pl", "py", "rst", "s", "sql", "srt", "text", "txt", "vcf",
    "vtt", "xml", "kt", "java", "go", "rs", "swift", "yaml", "yml", "toml",
)
