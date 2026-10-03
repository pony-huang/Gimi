package github.ponyhuang.gimi.domain.modelcatalog.model

/** Agent 运行时解析后的模型配置（跨 data 模块传递，不暴露 data 层类型）。 */
data class ResolvedAgentModel(
    val serviceId: String,
    val protocol: ApiProtocol,
    val modelId: String,
    val apiKey: String,
    /** 已 trimEnd('/') 的 activeApiBaseUrl。 */
    val modelBaseUrl: String,
    /** 非空时使用设备文件推理，不创建远端 SDK 客户端。 */
    val localModel: LocalModelRuntimeConfig? = null,
)
