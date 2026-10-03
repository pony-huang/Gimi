package github.ponyhuang.gimi.domain.modelcatalog.model

/** 本地推理的硬件后端，不暴露 SDK 类型。 */
enum class LocalModelBackend { CPU, GPU }

/** 下载生命周期；只有 Ready 状态可加入模型选择。 */
enum class LocalModelDownloadStatus { NotDownloaded, Downloading, Verifying, Ready, Failed }

/** 可本地化展示的下载失败原因。 */
enum class LocalModelFailure { Network, Storage, Integrity, AccessDenied }

/** 官方模型版本的稳定元数据，bytes 是固定版本的完整文件大小。 */
data class LocalModelVariant(
    val id: String,
    val brandId: String,
    val name: String,
    val backend: LocalModelBackend,
    val bytes: Long,
)

/** 单个模型文件及其启用状态，progress 为 0 到 1 的下载进度。 */
data class LocalModelState(
    val variant: LocalModelVariant,
    val status: LocalModelDownloadStatus = LocalModelDownloadStatus.NotDownloaded,
    val enabled: Boolean = false,
    val progress: Float = 0f,
    val failure: LocalModelFailure? = null,
)

/** 本地目录快照；loading 表示尚未完成磁盘文件与启用配置恢复。 */
data class LocalModelCatalogState(
    val loading: Boolean = true,
    val models: List<LocalModelState> = emptyList(),
)

/** 已校验并启用的文件运行参数，由 data 提供路径，运行器选择相应 SDK 后端。 */
data class LocalModelRuntimeConfig(
    val modelPath: String,
    val backend: LocalModelBackend,
)

const val LOCAL_GEMMA_SERVICE_ID = "local-gemma4"
const val LOCAL_GEMMA_GROUP_ID = "gemma4"

/** 已下载且启用的版本形成独立本地服务，不伪造 API Key 或远端端点。 */
fun LocalModelCatalogState.enabledService(): LLMModelSetting = LLMModelSetting(
    id = LOCAL_GEMMA_SERVICE_ID,
    name = "Gemma 4",
    isEnabled = models.any { it.enabled && it.status == LocalModelDownloadStatus.Ready },
    isOfficialToolsEnabled = false,
    apiKey = "",
    apiBaseUrl = "",
    apiProtocol = ApiProtocol.Standard,
    supportedProtocols = emptyList(),
    anthropicBaseUrl = "",
    isLocal = true,
    groups = listOf(ModelGroup(
        id = LOCAL_GEMMA_GROUP_ID,
        name = "Gemma 4",
        models = models.filter { it.enabled && it.status == LocalModelDownloadStatus.Ready }
            .map { Model(id = it.variant.id, name = it.variant.name,
                capabilities = MultimodalCapabilities(vision = null, audioInput = null, documentInput = null)) },
    )),
)
