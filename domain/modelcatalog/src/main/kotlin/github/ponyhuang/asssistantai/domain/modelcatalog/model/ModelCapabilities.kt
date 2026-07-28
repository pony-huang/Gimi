package github.ponyhuang.asssistantai.domain.modelcatalog.model

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
    val supportedMimeTypes: List<String> = listOf("audio/wav", "audio/mp3", "audio/mpeg", "audio/mp4", "audio/ogg"),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    override val maxInlineBytes: Long? = 10 * 1024 * 1024,
    val maxUploadBytes: Long = 512 * 1024 * 1024,
) : HasIngestMode

data class DocumentInputCapability(
    val supportedMimeTypes: List<String> = listOf("application/pdf"),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    override val maxInlineBytes: Long? = 10 * 1024 * 1024,
    val maxUploadBytes: Long = 512 * 1024 * 1024,
) : HasIngestMode

data class MultimodalCapabilities(
    val vision: VisionCapability? = null,
    val audioInput: AudioInputCapability? = null,
    val documentInput: DocumentInputCapability? = null,
) {
    val supportsImages: Boolean get() = vision != null
    val supportsAudio: Boolean get() = audioInput != null
    val supportsDocuments: Boolean get() = documentInput != null
    val isMultimodal: Boolean get() = vision != null || audioInput != null || documentInput != null
}
