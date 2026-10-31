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
        val historicalDeclarations = contents.flatMap(::inlineDeclarations)
        // DeepSeek 只接受 tool_addition，不支持 tool_removal。需要撤下旧工具时，
        // 仅在本次请求使用当前声明，去掉历史加载标记；不改写持久化历史或重发全部目录。
        if (historicalDeclarations.any { it.name !in activeNames }) return params
        val offered = mutableSetOf<String>()
        val messages = buildList {
            for (content in contents) {
                val definitions = inlineDeclarations(content)
                if (content.role == Role.SYSTEM && content.parts.any {
                    INLINE_DEFINITIONS in it.partMetadata.orEmpty()
                }) {
                    val blocks = definitions.filter { offered.add(it.name) }.map { declaration ->
                        mapOf("type" to "tool_addition", "tool" to mapOf(
                            "type" to "tool_reference", "name" to declaration.name,
                        ))
                    }
                    if (blocks.isNotEmpty()) add(inlineSystemMessage(blocks))
                } else {
                    convertContent(content)?.let(::add)
                }
            }
        }
        builder.messages(messages)
        val declared = standard.toAnthropicTools(request.config.tools)
        // 内联路径只添加当前仍可用的工具；撤销场景已转为普通声明。
        val snapshots = request.config.systemInstruction?.parts.orEmpty().flatMap {
            (it.partMetadata?.get(DEFERRED_CATALOG) as? List<*>).orEmpty().filterIsInstance<String>()
        }.map(::decodeDeclaration) + historicalDeclarations
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

    // SDK 已支持 system 角色；尚未建模的内联块通过原始 JSON 扩展发送。
    private fun inlineSystemMessage(blocks: List<*>): MessageParam = MessageParam.builder()
        .role(MessageParam.Role.SYSTEM)
        .contentOfBlockParams(blocks.map { jsonMapper.convertValue(it, ContentBlockParam::class.java) })
        .build()

}
