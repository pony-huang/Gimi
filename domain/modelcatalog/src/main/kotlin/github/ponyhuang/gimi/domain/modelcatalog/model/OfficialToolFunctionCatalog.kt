package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 会话中可展示的官方工具及其来源服务。
 *
 * @property toolId 厂商唯一的官方工具目录 ID。
 * @property serviceId 提供凭据和执行配置的模型服务 ID。
 */
data class OfficialToolAvailability(
    val toolId: String,
    val serviceId: String,
)

/** 官方工具目录契约，由 modelcatalog 实现；UI 与 Agent 查询同一份声明和可用性。 */
interface OfficialToolFunctionCatalog {

    /** 全部纯目录声明，不包含凭据与运行时工具实例。 */
    val all: List<OfficialToolSpec>

    /** 按当前请求模型与已启用的来源服务解析可用声明。 */
    fun availableSpecsFor(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec>

    /** 按需检索可以发现的已开启本地工具来源。 */
    fun enabledSearchCandidateSpecs(): List<OfficialToolSpec>

    /** 请求协议需要保留为厂商远端执行声明的名字。 */
    fun providerDeclaredWireNames(
        serviceId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): Set<String> = availableSpecsFor(serviceId, protocol, modelId)
        .mapNotNull { (it.binding as? OfficialToolBinding.ProviderDeclaration)?.wireName }
        .toSet()

    /**
     * Returns the tool ids the given service supports under [protocol],
     * regardless of the concrete model. Tool ids are vendor-unique; model
     * family narrowing (if any) is applied later by the agent runtime.
     *
     * 实现为静态目录查询,无网络 IO;UI 与会话配置初始化同步调用。
     */
    fun supportedToolIds(serviceId: String, protocol: ApiProtocol): Set<String>

    /**
     * 返回当前聊天模型可用的官方工具：当前服务保留原生工具，其他已开启服务只贡献
     * 可独立调用的 API 工具。
     */
    fun availableTools(
        activeService: LLMModelSetting,
        activeModelId: String,
    ): List<OfficialToolAvailability>

    /** Returns the functions currently available for [toolId], or empty on failure. */
    suspend fun listFunctions(toolId: String): List<OfficialToolFunction>
}
