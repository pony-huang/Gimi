package github.ponyhuang.gimi.domain.modelcatalog.model

/** How a model accepts files of a given media type. */
enum class FileIngestMode {
    /** This media type is not supported by the model. */
    NOT_SUPPORTED,
    /** Files are sent as base64-encoded inline data in the request body. */
    INLINE,
    /** Files must first be uploaded to the vendor's file management API, then referenced by file_id. */
    FILE_REFERENCE,
}

interface HasIngestMode {
    val ingestMode: FileIngestMode
    val maxInlineBytes: Long?
}

data class VisionCapability(
    val supportedMimeTypes: List<String> = listOf("image/jpeg", "image/png", "image/gif", "image/webp"),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    val supportsVideo: Boolean = false,
) : HasIngestMode {
    override val maxInlineBytes: Long? get() = null
}

data class AudioInputCapability(
    val supportedMimeTypes: List<String> = listOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3"),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    override val maxInlineBytes: Long? = 50 * 1024 * 1024,
    val maxUploadBytes: Long = 512 * 1024 * 1024,
) : HasIngestMode

data class DocumentInputCapability(
    val supportedMimeTypes: List<String> = listOf(
        "application/pdf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/json",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    ),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    override val maxInlineBytes: Long? = 50 * 1024 * 1024,
    val maxUploadBytes: Long = 512 * 1024 * 1024,
) : HasIngestMode

data class MultimodalCapabilities(
    val vision: VisionCapability? = VisionCapability(),
    val audioInput: AudioInputCapability? = AudioInputCapability(),
    val documentInput: DocumentInputCapability? = DocumentInputCapability(),
) {
    val supportsImages: Boolean get() = vision != null
    val supportsAudio: Boolean get() = audioInput != null
    val supportsDocuments: Boolean get() = documentInput != null
    val isMultimodal: Boolean get() = vision != null || audioInput != null || documentInput != null
}

fun MultimodalCapabilities.withDefaultAttachmentCapabilities(): MultimodalCapabilities = copy(
    vision = vision ?: VisionCapability(),
    audioInput = audioInput ?: AudioInputCapability(),
    documentInput = documentInput ?: DocumentInputCapability(),
)
