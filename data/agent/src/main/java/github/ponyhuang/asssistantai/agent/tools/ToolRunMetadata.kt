package github.ponyhuang.asssistantai.agent.tools

import com.google.adk.kt.agents.ReadonlyContext
import github.ponyhuang.asssistantai.agent.ModelRuntimeMetadata
import github.ponyhuang.asssistantai.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.asssistantai.domain.conversation.model.ToolAccessMode
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol

/**
 * 模型运行信息与会话工具配置的 RunConfig 透传编解码。
 *
 * `AgentChatRunner.send` 把配置拍平进 `RunConfig.customMetadata`，各 Toolset/BaseTool
 * 在 `getTools(readonlyContext)` / `processLlmRequest(toolContext, ...)` 时按请求读取并
 * 自行过滤 —— 因此 Agent 构建期不再绑定会话工具选择，勾选变化不会触发 Agent 重建。
 *
 * 约束：ADK 会把 `customMetadata` 合并进每个持久化 Event，Room 侧的 `AnySerializer`
 * 只接受 JSON-native 值（String/Boolean/Number/List/Map），所以这里必须把
 * [ConversationToolConfiguration] 拍平成基本类型容器，不能直接放 data class。
 * 模型 metadata 只包含服务、协议、模型和端点，严禁写入 API Key。
 *
 * 语义约定：metadata 中不存在工具配置 key 表示「无会话级配置」（沿用全局默认）；
 * 存在但集合为空表示「会话显式不启用任何项」——两者不可混淆。
 */
object ToolRunMetadata {
    private const val KEY_MODEL_SERVICE_ID = "selkie.model.service_id"
    private const val KEY_MODEL_PROTOCOL = "selkie.model.protocol"
    private const val KEY_MODEL_ID = "selkie.model.id"
    private const val KEY_MODEL_BASE_URL = "selkie.model.base_url"
    private const val KEY_PRESENT = "selkie.tool_config.present"
    private const val KEY_LOCAL_TOOL_IDS = "selkie.tool_config.local_tool_ids"
    private const val KEY_MCP_SERVER_IDS = "selkie.tool_config.mcp_server_ids"
    private const val KEY_OFFICIAL_FUNCTIONS = "selkie.tool_config.official_functions"
    private const val KEY_TOOL_ACCESS_MODE = "selkie.tool_config.access_mode"
    private const val KEY_ALLOW_CONFIRMATION_TOOLS = "selkie.allow_confirmation_required_tools"

    /** 编码为 JSON-native metadata；[toolConfiguration] 为 null 时不写任何配置 key。 */
    fun of(
        modelRuntime: ModelRuntimeMetadata,
        toolConfiguration: ConversationToolConfiguration?,
        allowConfirmationRequiredTools: Boolean,
    ): Map<String, Any> {
        val metadata = mutableMapOf<String, Any>(
            KEY_MODEL_SERVICE_ID to modelRuntime.serviceId,
            KEY_MODEL_PROTOCOL to modelRuntime.baseType.name,
            KEY_MODEL_ID to modelRuntime.modelId,
            KEY_MODEL_BASE_URL to modelRuntime.fullBaseUrl,
            KEY_ALLOW_CONFIRMATION_TOOLS to allowConfirmationRequiredTools,
        )
        if (toolConfiguration != null) {
            metadata[KEY_PRESENT] = true
            metadata[KEY_LOCAL_TOOL_IDS] = toolConfiguration.enabledLocalToolIds.toList()
            metadata[KEY_MCP_SERVER_IDS] = toolConfiguration.enabledMcpServerIds.toList()
            metadata[KEY_OFFICIAL_FUNCTIONS] =
                toolConfiguration.enabledOfficialFunctionIdsByService.mapValues { (_, byTool) ->
                    byTool.mapValues { (_, ids) -> ids.toList() }
                }
            metadata[KEY_TOOL_ACCESS_MODE] = toolConfiguration.toolAccessMode.name
        }
        return metadata
    }

    /** 解码不含凭据的模型运行信息；缺失或协议无效时返回 null。 */
    fun modelRuntime(metadata: Map<String, Any>?): ModelRuntimeMetadata? {
        val serviceId = metadata?.get(KEY_MODEL_SERVICE_ID) as? String ?: return null
        val protocol = (metadata[KEY_MODEL_PROTOCOL] as? String)
            ?.let { name -> runCatching { ApiProtocol.valueOf(name) }.getOrNull() }
            ?: return null
        val modelId = metadata[KEY_MODEL_ID] as? String ?: return null
        val baseUrl = metadata[KEY_MODEL_BASE_URL] as? String ?: return null
        return ModelRuntimeMetadata(
            serviceId = serviceId,
            baseType = protocol,
            modelId = modelId,
            fullBaseUrl = baseUrl,
        )
    }

    /** 读取会话工具配置；metadata 未携带时返回 null（无会话级配置）。 */
    fun toolConfiguration(metadata: Map<String, Any>?): ConversationToolConfiguration? {
        if (metadata == null || metadata[KEY_PRESENT] != true) return null
        val official = (metadata[KEY_OFFICIAL_FUNCTIONS] as? Map<*, *>)
            .orEmpty()
            .mapNotNull { (service, byTool) ->
                val toolMap = (byTool as? Map<*, *>)
                    .orEmpty()
                    .mapNotNull { (tool, ids) ->
                        val idSet = (ids as? List<*>)?.filterIsInstance<String>()?.toSet()
                            ?: return@mapNotNull null
                        (tool as? String)?.let { it to idSet }
                    }
                    .toMap()
                (service as? String)?.let { it to toolMap }
            }
            .toMap()
        return ConversationToolConfiguration(
            enabledLocalToolIds = metadata.stringSet(KEY_LOCAL_TOOL_IDS),
            enabledMcpServerIds = metadata.stringSet(KEY_MCP_SERVER_IDS),
            enabledOfficialFunctionIdsByService = official,
            toolAccessMode = when (metadata[KEY_TOOL_ACCESS_MODE] as? String) {
                ToolAccessMode.ON_DEMAND.name -> ToolAccessMode.ON_DEMAND
                ToolAccessMode.ALWAYS_AVAILABLE.name -> ToolAccessMode.ALWAYS_AVAILABLE
                else -> ToolAccessMode.ALWAYS_AVAILABLE
            },
        )
    }

    /** 是否允许需要用户确认的工具；缺省 true。 */
    fun allowConfirmationRequiredTools(metadata: Map<String, Any>?): Boolean =
        (metadata?.get(KEY_ALLOW_CONFIRMATION_TOOLS) as? Boolean) ?: true

    private fun Map<String, Any>.stringSet(key: String): Set<String> =
        (this[key] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
}

/** 从 invocation 的 RunConfig 读取不含凭据的模型运行信息。 */
fun ReadonlyContext?.modelRuntimeMetadataOrNull(): ModelRuntimeMetadata? =
    ToolRunMetadata.modelRuntime(this?.runConfig?.customMetadata)

/** 从 invocation 上下文读取会话工具配置；无上下文或未携带配置时为 null。 */
fun ReadonlyContext?.toolConfigurationOrNull(): ConversationToolConfiguration? =
    ToolRunMetadata.toolConfiguration(this?.runConfig?.customMetadata)

/** 从 invocation 上下文读取确认工具开关；缺省 true。 */
fun ReadonlyContext?.allowConfirmationRequiredTools(): Boolean =
    ToolRunMetadata.allowConfirmationRequiredTools(this?.runConfig?.customMetadata)
