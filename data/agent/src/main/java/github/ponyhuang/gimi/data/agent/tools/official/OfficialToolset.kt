package github.ponyhuang.gimi.data.agent.tools.official

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.modelRuntimeMetadataOrNull
import github.ponyhuang.gimi.data.agent.tools.toolConfigurationOrNull
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource

/**
 * Request-scoped official tools integrated through ADK's [Toolset] lifecycle.
 *
 * `LlmAgentTurn.prepareRequest` runs [processLlmRequest] with the current invocation context.
 * Each resolved [BaseTool] then processes that same request, which both appends
 * its declaration and records the executable instance in ADK's request-local tool map. The normal
 * [getTools] hook intentionally returns an empty list so ADK does not register the same tools twice.
 *
 * 会话级函数选择不再烘进模型配置，而是每次请求从 invocation 上下文
 * （见 [toolConfigurationOrNull]）读取，因此会话勾选变化不需要重建 Agent。
 */
interface OfficialToolset : Toolset {

    /**
     * 解析当前请求可用的官方工具。
     *
     * @param config 当前 invocation RunConfig 携带的非敏感模型运行信息。
     * @param selection 当前 invocation 的会话级工具选择；null 表示沿用默认启用语义。
     */
    suspend fun resolveTools(
        config: ModelRuntimeMetadata,
        selection: ConversationToolConfiguration?,
    ): List<BaseTool>

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        val readonlyContext = toolContext.context
        val config = readonlyContext.modelRuntimeMetadataOrNull() ?: return llmRequest
        val selection = readonlyContext.toolConfigurationOrNull()
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
    toolId: String,
): Boolean = this == null || enabledOfficialFunctionIds(toolId).isNotEmpty()

/**
 * Declaration-only tool executed by the remote model provider, never by the local Agent runtime.
 *
 * @property declarationName 发给厂商 API 的保留声明字面量(如 `web_search`),由各协议
 * 模型适配层转换为厂商原生 wire 格式;目录级厂商唯一 ID 不用于线上声明。
 */
internal class OfficialBuiltInTool(
    val declarationName: String,
) : BaseTool(name = declarationName, description = declarationName) {

    override fun declaration(): FunctionDeclaration =
        FunctionDeclaration(name = name, description = description)

    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any =
        throw UnsupportedOperationException("$name is executed by the model provider")
}
