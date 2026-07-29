package github.ponyhuang.asssistantai.agent.tools.official

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.asssistantai.agent.ConfiguredModel
import github.ponyhuang.asssistantai.agent.ModelConfig
import github.ponyhuang.asssistantai.agent.tools.toolConfigurationOrNull
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration

/**
 * Request-scoped official tools integrated through ADK's [Toolset] lifecycle.
 *
 * `LlmAgentTurn.prepareRequest` runs [processLlmRequest] after the request processors have attached
 * the current model. Each resolved [BaseTool] then processes that same request, which both appends
 * its declaration and records the executable instance in ADK's request-local tool map. The normal
 * [getTools] hook intentionally returns an empty list so ADK does not register the same tools twice.
 *
 * 会话级函数选择不再烘进模型配置，而是每次请求从 invocation 上下文
 * （RunConfig metadata，见 [toolConfigurationOrNull]）读取 —— 会话勾选变化不需要重建 Agent。
 */
interface OfficialToolset : Toolset {

    /**
     * 解析当前请求可用的官方工具。
     *
     * @param config 请求模型携带的服务级不可变配置（支持哪些官方工具）。
     * @param selection 会话级函数勾选；为 null 表示无会话配置，服务级支持的工具全部启用。
     */
    suspend fun resolveTools(
        config: ModelConfig,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool>

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val config = (llmRequest.model as? ConfiguredModel)?.modelConfig ?: return llmRequest
        val selection = toolContext.context.toolConfigurationOrNull()
        return resolveTools(config, selection).fold(llmRequest) { request, tool ->
            tool.processLlmRequest(toolContext, request)
        }
    }

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = emptyList()
}

/**
 * 官方工具在会话中是否启用。
 *
 * 无会话配置（null）时视为全部启用；有配置时要求该工具至少有一个函数被勾选
 * （与旧的 `ModelConfig.forConversation` 过滤语义一致）。
 */
internal fun ConversationToolConfiguration?.isOfficialToolEnabled(
    serviceId: String,
    toolId: String,
): Boolean = this == null || enabledOfficialFunctionIds(serviceId, toolId).isNotEmpty()

/**
 * 需要进入 Tool access 动态候选目录的官方函数工具集。
 *
 * 厂商原生工具不实现该接口，仍由 [OfficialToolset.processLlmRequest] 直接注入；
 * 会展开为普通函数声明的工具集实现本接口，由统一动态 Toolset 控制暴露时机。
 */
interface DynamicOfficialToolset : OfficialToolset {
    val sourceId: String
    val sourceDisplayName: String
}

/** Declaration-only tool executed by the remote model provider, never by the local Agent runtime. */
internal class OfficialBuiltInTool(
    toolId: String,
) : BaseTool(name = toolId, description = toolId) {

    override fun declaration(): FunctionDeclaration =
        FunctionDeclaration(name = name, description = description)

    override suspend fun run(context: ToolContext, args: Map<String, Any>): Any =
        throw UnsupportedOperationException("$name is executed by the model provider")
}
