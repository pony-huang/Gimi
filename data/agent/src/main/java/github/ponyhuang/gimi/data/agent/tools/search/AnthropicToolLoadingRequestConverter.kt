package github.ponyhuang.gimi.data.agent.tools.search

import com.anthropic.core.JsonValue.Companion.from
import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.ToolUnion
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Tool as AdkTool
import github.ponyhuang.gimi.data.agent.model.ClaudeRequestConverter
import github.ponyhuang.gimi.data.agent.model.normalizeForAnthropic

/** 工具加载层将持久化的内部事件翻译成厂商请求，不让通用 Claude 转换器依赖搜索实现。 */
internal class AnthropicToolLoadingRequestConverter(
    name: String,
    maxTokens: Int,
    private val loadingMode: ToolLoadingMode,
) {
    private val standard = ClaudeRequestConverter(name, maxTokens)
    private val jsonMapper = jsonMapper()

    fun convert(request: LlmRequest): MessageCreateParams {
        val contents = request.contents.normalizeForAnthropic()
        val cleanRequest = request.copy(contents = contents.filterNot { content ->
            content.role == Role.SYSTEM && content.parts.any { INLINE_DEFINITIONS in it.partMetadata.orEmpty() }
        })
        val params = standard.convert(cleanRequest)
        if (loadingMode == ToolLoadingMode.RESULT) return params
        val builder = params.toBuilder()
        val activeNames = request.config.tools.orEmpty().flatMap { it.functionDeclarations.orEmpty() }
            .mapTo(mutableSetOf()) { it.name }
        val offered = mutableSetOf<String>()
        val messages = buildList {
            for (content in contents) {
                val definitions = inlineDeclarations(content)
                if (content.role == Role.SYSTEM && content.parts.any {
                    INLINE_DEFINITIONS in it.partMetadata.orEmpty()
                }) {
                    val names = definitions.mapTo(mutableSetOf()) { it.name }
                    val blocks = (offered - names).map(::toolRemoval) + definitions.map { declaration ->
                        mapOf("type" to "tool_addition", "tool" to mapOf(
                            "type" to "tool_reference", "name" to declaration.name,
                        ))
                    }
                    if (blocks.isNotEmpty()) add(inlineSystemMessage(blocks))
                    offered.clear()
                    offered.addAll(names)
                } else {
                    convertContent(content)?.let(::add)
                }
            }
            val withdrawn = offered - activeNames
            if (withdrawn.isNotEmpty()) {
                val blocks = withdrawn.map(::toolRemoval)
                if (lastOrNull()?.role() == MessageParam.Role.SYSTEM) {
                    // 将撤销追加到同一 system 消息，保留历史定义，不改写早期前缀。
                    val previous = removeAt(lastIndex)
                    val raw = jsonMapper.convertValue(previous, Map::class.java)
                    add(inlineSystemMessage((raw["content"] as List<*>) + blocks))
                } else {
                    add(inlineSystemMessage(blocks))
                }
            }
        }
        builder.messages(messages)
        val declared = standard.toAnthropicTools(request.config.tools)
        // 历史中被撤下的工具也必须保留可解析的引用，最后的 removal 控制当前可用性。
        val snapshots = request.config.systemInstruction?.parts.orEmpty().flatMap {
            (it.partMetadata?.get(DEFERRED_CATALOG) as? List<*>).orEmpty().filterIsInstance<String>()
        }.map(::decodeDeclaration) + contents.flatMap(::inlineDeclarations)
        val declarations = snapshots.distinctBy { it.name }
        val deferred = standard.toAnthropicTools(listOf(AdkTool(functionDeclarations = declarations)))
            .map { tool ->
                ToolUnion.ofTool(tool.asTool().toBuilder()
                    .putAdditionalProperty("defer_loading", from(true)).build())
            }
        val deferredNames = deferred.map { it.asTool().name() }.toSet()
        val tools = declared.filter { !it.isTool() || it.asTool().name() !in deferredNames } + deferred
        if (tools.isNotEmpty()) builder.tools(tools)
        return builder.build()
    }

    private fun convertContent(content: Content): MessageParam? {
        val message = standard.contentToAnthropicMessageParam(content) ?: return null
        val searches = content.parts.mapNotNull { it.functionResponse }
            .filter { it.name == TOOL_SEARCH_NAME && it.response.isEmpty() }.map { it.id }.toSet()
        if (searches.isEmpty()) return message
        return message.toBuilder().contentOfBlockParams(message.content().asBlockParams().map { block ->
            if (block.isToolResult() && block.asToolResult().toolUseId() in searches) {
                ContentBlockParam.ofToolResult(block.asToolResult().toBuilder().content("").build())
            } else block
        }).build()
    }

    @OptIn(FrameworkInternalApi::class)
    private fun decodeDeclaration(snapshot: String): FunctionDeclaration =
        adkJson.decodeFromString(FunctionDeclaration.serializer(), snapshot)

    @OptIn(FrameworkInternalApi::class)
    private fun inlineDeclarations(content: Content): List<FunctionDeclaration> =
        content.parts.flatMap { part ->
            (part.partMetadata?.get(INLINE_DEFINITIONS) as? List<*>).orEmpty()
                .filterIsInstance<String>().map {
                    adkJson.decodeFromString(FunctionDeclaration.serializer(), it)
                }
        }

    private fun toolRemoval(name: String): Map<String, Any> = mapOf(
        "type" to "tool_removal",
        "tool" to mapOf("type" to "tool_reference", "name" to name),
    )

    // SDK 已支持 system 角色；尚未建模的内联块通过原始 JSON 扩展发送。
    private fun inlineSystemMessage(blocks: List<*>): MessageParam = MessageParam.builder()
        .role(MessageParam.Role.SYSTEM)
        .contentOfBlockParams(blocks.map { jsonMapper.convertValue(it, ContentBlockParam::class.java) })
        .build()

}
