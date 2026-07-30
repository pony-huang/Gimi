package github.ponyhuang.gimi.agent.plugins

import android.util.Log
import com.google.adk.kt.agents.CallbackContext
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.ThinkingConfig
import github.ponyhuang.gimi.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.agent.AgentPrompts
import github.ponyhuang.gimi.agent.ConversationTitle
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import kotlinx.coroutines.flow.toList
import java.util.concurrent.ConcurrentHashMap

/**
 * 通过 ADK 回调携带首轮标题上下文，并将最终标题保留在会话状态中
 * @author pony
 */
class ConversationGenerateTitlePlugin(
    private val agentLLMModelFactory: AgentLLMModelFactory,
    override val name: String = "conversation_plugin"
) : Plugin {

    /**
     * 根据初始问题回答，请求模型总结标题
     */
    private suspend fun generateTitle(
        agentModel: Model,
        userText: String,
        assistantText: String
    ): String? {

        val model = agentLLMModelFactory.selectFastModelConfig()?.let {
            agentLLMModelFactory.createModel(it)
        } ?: agentModel

        val prompt = """
            User message:
            $userText
            Assistant response:
            $assistantText
        """.trimIndent()
        val responses = model.generateContent(
            request = LlmRequest(
                model = model,
                contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = prompt)))),
                config = GenerateContentConfig(
                    systemInstruction = Content(
                        parts = listOf(
                            Part(
                                text = AgentPrompts.CONVERSATION_TITLE_INSTRUCTION,
                            ),
                        ),
                    ),
                    temperature = 0.2f,
                    maxOutputTokens = 32,
                    thinkingConfig = ThinkingConfig(false)
                ),
            )
        ).toList()
        responses.firstOrNull { !it.errorMessage.isNullOrBlank() }?.errorMessage?.let { message ->
            throw IllegalStateException("Title model request failed: $message")
        }

        fun textOf(index: Int): String? = responses[index].content?.parts
            ?.filter { it.thought != true }
            ?.mapNotNull { it.text }
            ?.joinToString("")
            ?.takeIf(String::isNotBlank)

        // Wait for the entire stream, prefer a model's full final answer when present, otherwise
        // fold its text deltas. The local OpenAI bridge emits text-only completions as deltas.
        val rawTitle = responses.indices.reversed()
            .firstOrNull { !responses[it].partial && textOf(it) != null }
            ?.let(::textOf)
            ?: responses.indices
                .filter { responses[it].partial }
                .mapNotNull(::textOf)
                .joinToString("")
                .takeIf(String::isNotBlank)
        if (rawTitle.isNullOrBlank()) {
            Log.w(TAG, "Title model returned no text content (responses=${responses.size})")
        } else {
            Log.i(TAG, "Title model returned ${rawTitle.length} characters")
        }
        return ConversationTitle.generated(rawTitle)
    }

    private fun isTitleFlowCompleted(context: CallbackContext): Boolean =
        context.state[STATE_TITLE_FLOW_COMPLETED] == true ||
                !((context.state[STATE_TITLE] as? String).isNullOrBlank())

    private fun completeTitleFlow(context: CallbackContext, title: String) {
        context.updateState(STATE_TITLE, title)
        context.updateState(STATE_TITLE_FLOW_COMPLETED, true)
    }

    private companion object {
        const val TAG = "conversation.plugin"
        const val STATE_TITLE = "conversation.title"
        const val STATE_TITLE_FLOW_COMPLETED = "conversation.title.completed"
        const val MAX_PROMPT_TEXT_LENGTH = 2_000
        const val PENDING_TITLE_TTL_MILLIS = 5 * 60 * 1_000L
    }

    private data class PendingTitle(
        val provisionalTitle: String = ConversationTitle.IMAGE_MESSAGE_TITLE,
        val userText: String = "",
        val assistantText: String? = null,
        val createdAtMillis: Long = System.currentTimeMillis(),
    )

    private val pendingTitles = ConcurrentHashMap<String, PendingTitle>()


    override suspend fun beforeModel(
        context: CallbackContext,
        request: LlmRequest
    ): CallbackChoice<LlmRequest, LlmResponse> {
        // 第一次 先获取用户信息作为标题
        val now = System.currentTimeMillis()
        pendingTitles.entries.removeIf { (_, pending) ->
            now - pending.createdAtMillis > PENDING_TITLE_TTL_MILLIS
        }
        if (isTitleFlowCompleted(context)) {
            Log.d(TAG, "Title flow already completed for ${context.invocationId}")
            return CallbackChoice.Continue(request)
        }
        if (!pendingTitles.containsKey(context.invocationId)) {
            val latestUserContent = request.contents.lastOrNull { it.role == Role.USER }
            val userText = latestUserContent?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
            val title = ConversationTitle.provisional(userText)
                ?: latestUserContent?.takeIf {
                    it.parts.any { part -> part.inlineData != null || part.fileData != null }
                }
                    ?.let { ConversationTitle.IMAGE_MESSAGE_TITLE }

            if (title != null) {
                val pending = PendingTitle(
                    provisionalTitle = title,
                    userText = userText.orEmpty().take(MAX_PROMPT_TEXT_LENGTH),
                )
                if (pendingTitles.putIfAbsent(context.invocationId, pending) == null) {
                    Log.i(TAG, "Provisional title prepared for ${context.invocationId}: $title")
                }
            }
        }
        return CallbackChoice.Continue(request)
    }

    override suspend fun afterModel(context: CallbackContext, response: LlmResponse): LlmResponse {
        // 获取最终结果
        val pending = pendingTitles[context.invocationId]
        if (pending != null && !response.partial) {
            response.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.takeIf(String::isNotBlank)
                ?.let {
                    pendingTitles[context.invocationId] = pending.copy(
                        assistantText = it.take(MAX_PROMPT_TEXT_LENGTH),
                    )
                    Log.i(TAG, "First assistant response captured for ${context.invocationId}")
                }
        }
        return response
    }

    override suspend fun afterAgent(context: CallbackContext): CallbackChoice<Unit, Content> {
        if (isTitleFlowCompleted(context)) {
            pendingTitles.remove(context.invocationId)
            return CallbackChoice.Continue(Unit)
        }
        val pending = pendingTitles.remove(context.invocationId)
        if (pending == null) {
            Log.d(TAG, "No pending title context for ${context.invocationId}")
            return CallbackChoice.Continue(Unit)
        }

        val assistantText = pending.assistantText
        if (assistantText.isNullOrBlank()) {
            Log.i(
                TAG,
                "No final assistant text for ${context.invocationId}; title generation skipped"
            )
            completeTitleFlow(context, pending.provisionalTitle)
            return CallbackChoice.Continue(Unit)
        }

        Log.i(TAG, "Generating AI title for ${context.invocationId}")
        val llmAgent = context.agent as LlmAgent
        val title = cancellationAwareRunCatching {
            generateTitle(
                llmAgent.model,
                userText = pending.userText,
                assistantText = assistantText,
            )
        }.onFailure { error ->
            Log.w(TAG, "Conversation title generation failed", error)
        }.getOrNull()

        if (title != null) {
            completeTitleFlow(context, title)
            Log.i(TAG, "AI title stored for ${context.invocationId}: $title")
        } else {
            // A failed title request must not hide the readable first-message fallback.
            completeTitleFlow(context, pending.provisionalTitle)
            Log.w(
                TAG,
                "AI title unavailable for ${context.invocationId}; keeping provisional title"
            )
        }
        return CallbackChoice.Continue(Unit)
    }

}
