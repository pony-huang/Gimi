package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 会话把已启用工具暴露给模型的方式。
 *
 * [ON_DEMAND] 始终先检索；[ALWAYS_AVAILABLE] 从当前用户轮次的第一次模型请求起
 * 加载全部已启用工具。
 */
@Serializable(with = ToolAccessModeSerializer::class)
enum class ToolAccessMode {
    ON_DEMAND,
    ALWAYS_AVAILABLE,
}

/**
 * 当前会话交给模型的推理深度。
 *
 * 四档命名与 ADK、OpenAI 的推理强度保持一致；具体模型适配层可按其协议映射为
 * `thinkingLevel`、`reasoning_effort` 或 extended-thinking 预算。
 */
@Serializable
enum class ReasoningEffort {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * 兼容历史持久化数据：旧版本曾把该字段写成 `"AUTO"`，未知值回退到 [ToolAccessMode.ALWAYS_AVAILABLE]。
 * kotlinx 默认对未知枚举名抛异常，这里用自定义 serializer 保留 codec 的归一化语义。
 */
object ToolAccessModeSerializer : KSerializer<ToolAccessMode> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ToolAccessMode", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ToolAccessMode) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ToolAccessMode {
        val name = decoder.decodeString()
        return ToolAccessMode.entries.firstOrNull { it.name == name }
            ?: ToolAccessMode.ALWAYS_AVAILABLE
    }
}

/**
 * Persistent, per-conversation tool selection state.
 *
 * Official tool selection is stored at function granularity. Tool ids are
 * vendor-unique, so a single-level map suffices: a tool id only appears in
 * [enabledOfficialFunctionIds] when at least one of its functions is selected.
 * When the available function list has not been loaded yet (e.g. before the
 * user opens the sub-page for the first time), we use [ALL_FUNCTIONS_MARKER]
 * as a sentinel meaning "every function of this tool is enabled" — it is
 * expanded to the real id set the moment the catalog becomes available.
 *
 * @property enabledMcpServerIds 当前会话选择的 MCP server ID。
 * @property pendingMcpCredentialServerId 当前会话最近一次等待补充认证凭据的 MCP server ID。
 * @property enabledOfficialFunctionIds 按官方工具(厂商唯一 ID)分组的函数选择。
 * @property toolAccessMode 当前会话采用的工具声明加载模式。
 * @property reasoningEffort 当前会话请求模型时采用的推理强度。
 */
@Serializable
data class ConversationToolConfiguration(
    val enabledMcpServerIds: Set<String> = emptySet(),
    val pendingMcpCredentialServerId: String? = null,
    val enabledOfficialFunctionIds: Map<String, Set<String>> = emptyMap(),
    val toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
) {
    fun enabledOfficialFunctionIds(toolId: String): Set<String> =
        enabledOfficialFunctionIds[toolId].orEmpty()

    fun enabledOfficialFunctionCount(toolId: String): Int =
        enabledOfficialFunctionIds(toolId).size

    /**
     * First-encounter default: enable every supported tool by seeding each
     * entry with the [ALL_FUNCTIONS_MARKER] sentinel. The actual function ids
     * are loaded lazily by the UI; when the catalog answers, the caller must
     * invoke [expandOfficialFunctionsMarker] to resolve the sentinel into
     * concrete ids.
     */
    fun initializeOfficialFunctions(supportedToolIds: Set<String>): ConversationToolConfiguration {
        if (enabledOfficialFunctionIds.keys.containsAll(supportedToolIds)) return this
        val seeded = supportedToolIds.associateWith { setOf(ALL_FUNCTIONS_MARKER) }
        return copy(enabledOfficialFunctionIds = enabledOfficialFunctionIds + seeded)
    }

    /**
     * Toggle a single function. If the tool currently uses the
     * [ALL_FUNCTIONS_MARKER] sentinel, it is first expanded to [supportedFunctionIds]
     * so the write never persists the marker alongside specific ids.
     */
    fun setOfficialFunctionEnabled(
        toolId: String,
        functionId: String,
        supportedFunctionIds: Set<String>,
        enabled: Boolean,
    ): ConversationToolConfiguration {
        val expanded = expandMarker(enabledOfficialFunctionIds, toolId, supportedFunctionIds)
        val current = expanded[toolId].orEmpty()
        val updated = if (enabled) current + functionId else current - functionId
        return copy(enabledOfficialFunctionIds = expanded + (toolId to updated))
    }

    /**
     * Replace [ALL_FUNCTIONS_MARKER] with the concrete [supportedFunctionIds]
     * for [toolId]. No-op when the marker is absent or already expanded. Returns
     * the configuration without persisting if the tool has no selection at all.
     */
    fun expandOfficialFunctionsMarker(
        toolId: String,
        supportedFunctionIds: Set<String>,
    ): ConversationToolConfiguration {
        if (supportedFunctionIds.isEmpty()) return this
        val current = enabledOfficialFunctionIds[toolId] ?: return this
        if (ALL_FUNCTIONS_MARKER !in current) return this
        return copy(
            enabledOfficialFunctionIds =
                enabledOfficialFunctionIds + (toolId to supportedFunctionIds),
        )
    }

    fun sanitize(availableMcpServerIds: Set<String>): ConversationToolConfiguration = copy(
        enabledMcpServerIds = enabledMcpServerIds intersect availableMcpServerIds,
    )

    private fun expandMarker(
        perTool: Map<String, Set<String>>,
        toolId: String,
        supportedFunctionIds: Set<String>,
    ): Map<String, Set<String>> {
        val current = perTool[toolId] ?: return perTool
        if (ALL_FUNCTIONS_MARKER !in current) return perTool
        if (supportedFunctionIds.isEmpty()) return perTool
        return perTool + (toolId to supportedFunctionIds)
    }

    companion object {
        /** Sentinel meaning "every function of this tool is enabled". */
        const val ALL_FUNCTIONS_MARKER: String = "__all__"
    }
}
