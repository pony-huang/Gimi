package github.ponyhuang.gimi.domain.modelaccess

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.FileIngestMode

/**
 * 解析后的模型配置，模型工厂创建协议会话时使用。
 *
 * 只描述当前请求的不可变模型运行参数（服务、协议、模型 ID、端点和凭据）。
 * 会话级工具勾选仍由 invocation context 提供，不与凭据混存。
 *
 * @property serviceId 模型服务实例 ID，用于匹配当前会话的官方函数选择与凭据归属。
 * @property templateId 厂商模板 ID；厂商工具支持矩阵与协议特例按模板匹配。null 表示自定义服务。
 * @property baseType 当前连接使用的 API 协议。
 * @property modelId 实际请求模型 ID，也是厂商模型家族判断的唯一依据。
 * @property apiKey 当前连接的生效凭据，仅保留在请求模型配置中。
 * @property fullBaseUrl 已解析到协议所需层级的连接端点。
 */
data class ModelConfig(
    val serviceId: String,
    val templateId: String?,
    val baseType: ApiProtocol,
    val modelId: String,
    val apiKey: String,
    val fullBaseUrl: String,
    val supportsImages: Boolean = false,
    /** 文档进入方式；FILE_REFERENCE 表示实例已开启厂商文件上传（协议适配器支持 file 块），PATH_TEXT 表示文档以本地路径文本送达、不传输内容。 */
    val documentIngestMode: FileIngestMode = FileIngestMode.INLINE,
)

/**
 * 可安全写入 ADK `RunConfig.customMetadata` 的模型运行信息。
 *
 * 该类型刻意不包含 API Key：ADK 会把 custom metadata 合并进持久化 Event，
 * 凭据必须在官方工具真正执行前根据 [serviceId] 从安全配置源重新解析。
 *
 * @property serviceId 模型服务实例 ID。
 * @property templateId 厂商模板 ID；null 表示自定义服务。
 * @property baseType 当前请求使用的协议。
 * @property modelId 实际模型 ID。
 * @property fullBaseUrl 当前协议对应的完整服务端点。
 */
data class ModelRuntimeMetadata(
    val serviceId: String,
    val templateId: String?,
    val baseType: ApiProtocol,
    val modelId: String,
    val fullBaseUrl: String,
    val supportsImages: Boolean = false,
    val documentIngestMode: FileIngestMode = FileIngestMode.INLINE,
)

fun ModelConfig.toRuntimeMetadata(): ModelRuntimeMetadata = ModelRuntimeMetadata(
    serviceId = serviceId,
    templateId = templateId,
    baseType = baseType,
    modelId = modelId,
    fullBaseUrl = fullBaseUrl,
    supportsImages = supportsImages,
    documentIngestMode = documentIngestMode,
)
