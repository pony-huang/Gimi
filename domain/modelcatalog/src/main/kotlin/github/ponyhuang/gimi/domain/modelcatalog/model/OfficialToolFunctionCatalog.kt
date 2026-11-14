package github.ponyhuang.gimi.domain.modelcatalog.model

/**
 * 会话中可展示的官方工具及其来源实例。
 *
 * @property toolId 厂商唯一的官方工具目录 ID。
 * @property serviceId 提供凭据的模型服务实例 ID；凭据在执行期按实例解析。
 */
data class OfficialToolAvailability(
    val toolId: String,
    val serviceId: String,
)

/** 官方工具目录契约，由 modelcatalog 实现；UI 与 Agent 查询同一份声明和可用性。 */
interface OfficialToolFunctionCatalog {

    /** 全部纯目录声明，不包含凭据与运行时工具实例。 */
    val all: List<OfficialToolSpec>

    /** 按当前请求模型与已启用的来源实例解析可用声明。 */
    fun availableSpecsFor(
        templateId: String,
        protocol: ApiProtocol,
        modelId: String,
    ): List<OfficialToolSpec>

    /** 按需检索可以发现的已开启本地工具来源。 */
    fun enabledSearchCandidateSpecs(): List<OfficialToolSpec>

    /**
     * Returns the tool ids the given template supports under [protocol],
     * regardless of the concrete model. Tool ids are vendor-unique; model
     * family narrowing (if any) is applied later by the agent runtime.
     *
     * 实现为静态目录查询,无网络 IO;UI 与会话配置初始化同步调用。
     */
    fun supportedToolIds(templateId: String, protocol: ApiProtocol): Set<String>

    /**
     * 返回当前聊天模型可用的官方工具：当前实例的模板保留原生工具，其他已开启实例只
     * 贡献可独立调用的 API 工具。
     */
    fun availableTools(
        activeInstance: ModelServiceInstance,
        activeModelId: String,
    ): List<OfficialToolAvailability>

    /** Returns the functions currently available for [toolId], or empty on failure. */
    suspend fun listFunctions(toolId: String): List<OfficialToolFunction>
}
