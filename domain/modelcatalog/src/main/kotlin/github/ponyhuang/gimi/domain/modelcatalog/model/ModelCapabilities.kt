package github.ponyhuang.gimi.domain.modelcatalog.model

import kotlinx.serialization.Serializable

/** How a model accepts files of a given media type. */
@Serializable
enum class FileIngestMode {
    /** This media type is not supported by the model. */
    NOT_SUPPORTED,
    /** Files are sent as base64-encoded inline data in the request body. */
    INLINE,
    /** Files must first be uploaded to the vendor's file management API, then referenced by file_id. */
    FILE_REFERENCE,
    /**
     * 文件内容不随请求传输，仅以本地绝对路径文本送达模型；是否用环境文件导入工具
     * （如 `import_file_to_environment`）读取由 agent 自行决定，环境未配置时亦不特殊处理。
     */
    PATH_TEXT,
}

interface HasIngestMode {
    val ingestMode: FileIngestMode
    val maxInlineBytes: Long?
}

@Serializable
data class VisionCapability(
    val supportedMimeTypes: List<String> = listOf("image/jpeg", "image/png", "image/gif", "image/webp"),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    val supportsVideo: Boolean = false,
) : HasIngestMode {
    // 计算属性，无 backing field，Gson/kotlinx 都不会序列化——与 Audio/Document 的 maxInlineBytes 不对称保持一致。
    override val maxInlineBytes: Long? get() = null
}

@Serializable
data class AudioInputCapability(
    val supportedMimeTypes: List<String> = listOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3"),
    override val ingestMode: FileIngestMode = FileIngestMode.INLINE,
    override val maxInlineBytes: Long? = 50 * 1024 * 1024,
    val maxUploadBytes: Long = 512 * 1024 * 1024,
) : HasIngestMode

@Serializable
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

/**
 * 模型可接收的多模态输入能力。
 *
 * 远端目录同步仅返回模型 ID，未确认的输入模态默认为空（null），避免未经确认即展示
 * 「视觉 / 文档 / 音频」徽标而造成误解。
 *
 * @property vision 图像（及可选视频）输入能力；null 表示未确认或不支持视觉输入。
 * @property audioInput 音频输入能力；null 表示未确认或不支持音频输入。
 * @property documentInput 文档输入能力；null 表示未确认或不支持文档输入。
 */
@Serializable
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

fun MultimodalCapabilities.withDefaultAttachmentCapabilities(): MultimodalCapabilities = copy(
    vision = vision ?: VisionCapability(),
    audioInput = audioInput ?: AudioInputCapability(),
    documentInput = documentInput ?: DocumentInputCapability(),
)
