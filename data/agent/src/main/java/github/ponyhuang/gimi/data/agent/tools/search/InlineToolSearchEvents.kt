package github.ponyhuang.gimi.data.agent.tools.search

import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.serialization.adkJson
import github.ponyhuang.gimi.data.agent.tools.modelRuntimeMetadataOrNull
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol

internal const val INLINE_DEFINITIONS = "selkie.inline_tool_definitions"
internal fun inlinePendingKey(agentName: String): String = "selkie.inline_tools.pending:$agentName"

/** 工具结果提交后再记录 system 事件；待提交快照随结果落库，取消后仍可恢复。 */
@OptIn(com.google.adk.kt.annotations.FrameworkInternalApi::class)
internal object InlineToolSearchEvents {

    suspend fun appendPending(
        context: ToolContext,
        request: LlmRequest,
        available: List<FunctionDeclaration>,
    ): LlmRequest {
        if (context.modelRuntimeMetadataOrNull()?.baseType != ApiProtocol.Anthropic) {
            return request
        }
        val invocation = context.invocationContext
        val key = inlinePendingKey(context.agentName)
        val selected = (context.state[ToolSearchToolset.STATE_KEY_LOADED_TOOLS] as? List<*>)
            .orEmpty().filterIsInstance<String>().toSet()
        val pending = (context.state[key] as? List<*>).orEmpty().filterIsInstance<String>()
        val definitions = if (pending.isNotEmpty()) pending.filter { snapshot ->
            adkJson.decodeFromString(FunctionDeclaration.serializer(), snapshot).name in available.map { it.name }
        } else available
            .filter { it.name in selected }.map {
                adkJson.encodeToString(FunctionDeclaration.serializer(), it)
            }
        val previous = request.contents.asReversed().flatMap { it.parts.asReversed() }
            .firstOrNull { INLINE_DEFINITIONS in it.partMetadata.orEmpty() }
            ?.partMetadata?.get(INLINE_DEFINITIONS) as? List<*>
        if (pending.isEmpty() && definitions == previous.orEmpty()) {
            return request
        }
        val content = Content(role = Role.SYSTEM, parts = listOf(
            Part(text = "Tool availability updated.", partMetadata = mapOf(INLINE_DEFINITIONS to definitions)),
        ))
        val service = requireNotNull(invocation.sessionService)
        // 清除待提交项与新增消息在同一次事件写入完成，避免重试重复插入。
        service.appendEvent(invocation.session, Event(
            invocationId = invocation.invocationId,
            author = context.agentName,
            branch = invocation.branch,
            content = content,
            actions = EventActions(stateDelta = mutableMapOf(key to emptyList<String>())),
        ))
        return request.copy(contents = request.contents + content)
    }
}
