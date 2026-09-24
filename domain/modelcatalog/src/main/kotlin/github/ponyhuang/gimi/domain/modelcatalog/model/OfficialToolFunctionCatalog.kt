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

/**
 * Domain interface for official (vendor built-in) tools. The implementation
 * lives in the agent layer and owns the vendor support matrix; UI and
 * conversation configuration query it through this contract.
 *
 * Implementations may fetch the manifest over the network (Kimi) or return a
 * static list (web search).
 */
interface OfficialToolFunctionCatalog {

    /**
     * Returns the tool ids the given service supports under [protocol],
     * regardless of the concrete model. Tool ids are vendor-unique; model
     * family narrowing (if any) is applied later by the agent runtime.
     *
     * 实现为静态注册表查询,无网络 IO;UI 与会话配置初始化同步调用。
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
