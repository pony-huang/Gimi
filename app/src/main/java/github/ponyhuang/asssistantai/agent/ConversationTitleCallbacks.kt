package github.ponyhuang.asssistantai.agent

import android.util.Log
import com.google.adk.kt.agents.CallbackContext
import com.google.adk.kt.callbacks.AfterAgentCallback
import com.google.adk.kt.callbacks.AfterModelCallback
import com.google.adk.kt.callbacks.BeforeModelCallback
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.ThinkingConfig
import kotlinx.coroutines.flow.toList
import java.util.concurrent.ConcurrentHashMap

/**
 * Carries first-turn title context through ADK callbacks and persists the final title in session state.
 *
 * The callbacks use the configured fast model for the title request, falling back to the chat
 * model when no valid fast model is available. The direct request keeps title generation out of
 * the conversation's event history and avoids exposing the chat agent's tools.
 */
class ConversationTitleCallbacks(
    private val model: Model,
) {
    fun beforeModel() = BeforeModelCallback { context, request ->
        if (isTitleFlowCompleted(context)) {
            Log.d(TAG, "Title flow already completed for ${context.invocationId}")
            return@BeforeModelCallback CallbackChoice.Continue(request)
        }
        if (!pendingTitles.containsKey(context.invocationId)) {
            val latestUserContent = request.contents.lastOrNull { it.role == Role.USER }
            val userText = latestUserContent?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
            val title = ConversationTitle.provisional(userText)
                ?: latestUserContent?.takeIf { it.parts.any { part -> part.inlineData != null } }
                    ?.let { ConversationTitle.IMAGE_MESSAGE_TITLE }

            if (title != null) {
                val pending = PendingTitle(
                    provisionalTitle = title,
                    userText = userText.orEmpty(),
                )
                if (pendingTitles.putIfAbsent(context.invocationId, pending) == null) {
                    Log.i(TAG, "Provisional title prepared for ${context.invocationId}: $title")
                }
            }
        }
        CallbackChoice.Continue(request)
    }

    fun afterModel() = AfterModelCallback { context, response ->
        val pending = pendingTitles[context.invocationId]
        if (pending != null && !response.partial) {
            response.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.takeIf(String::isNotBlank)
                ?.let {
                    pendingTitles[context.invocationId] = pending.copy(assistantText = it)
                    Log.i(TAG, "First assistant response captured for ${context.invocationId}")
                }
        }
        response
    }

    fun afterAgent() = AfterAgentCallback { context ->
        if (isTitleFlowCompleted(context)) {
            pendingTitles.remove(context.invocationId)
            return@AfterAgentCallback CallbackChoice.Continue(Unit)
        }
        val pending = pendingTitles.remove(context.invocationId)
        if (pending == null) {
            Log.d(TAG, "No pending title context for ${context.invocationId}")
            return@AfterAgentCallback CallbackChoice.Continue(Unit)
        }

        val assistantText = pending.assistantText
        if (assistantText.isNullOrBlank()) {
            Log.i(TAG, "No final assistant text for ${context.invocationId}; title generation skipped")
            completeTitleFlow(context, pending.provisionalTitle)
            return@AfterAgentCallback CallbackChoice.Continue(Unit)
        }

        Log.i(TAG, "Generating AI title for ${context.invocationId}")
        val title = runCatching {
            generateTitle(
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
            Log.w(TAG, "AI title unavailable for ${context.invocationId}; keeping provisional title")
        }
        CallbackChoice.Continue(Unit)
    }

    private suspend fun generateTitle(userText: String, assistantText: String): String? {
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
        const val TAG = "ConversationTitle"
        const val STATE_TITLE = "conversation.title"
        const val STATE_TITLE_FLOW_COMPLETED = "conversation.title.completed"
    }

    private data class PendingTitle(
        val provisionalTitle: String = ConversationTitle.IMAGE_MESSAGE_TITLE,
        val userText: String = "",
        val assistantText: String? = null,
    )

    private val pendingTitles = ConcurrentHashMap<String, PendingTitle>()
}
