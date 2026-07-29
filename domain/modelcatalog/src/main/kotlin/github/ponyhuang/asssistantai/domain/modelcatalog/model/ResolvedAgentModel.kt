package github.ponyhuang.asssistantai.domain.modelcatalog.model

/** Agent 运行时解析后的模型配置（跨 data 模块传递，不暴露 data 层类型）。 */
data class ResolvedAgentModel(
    val serviceId: String,
    val protocol: ApiProtocol,
    val modelId: String,
    val apiKey: String,
    /** 已 trimEnd('/') 的 activeApiBaseUrl。 */
    val modelBaseUrl: String,
    val supportedOfficialTools: List<String> = emptyList(),
)
