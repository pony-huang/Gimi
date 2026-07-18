package github.ponyhuang.asssistantai.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.FunctionCall
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.data.ConversationRepository
import github.ponyhuang.asssistantai.data.LLMModelSelectionCodec
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VoiceAgentResult(
    val sessionId: String,
    val responseText: String,
)

@Singleton
class VoiceAgentTaskExecutor @Inject constructor(
    @VoiceAgentRunner private val runner: AgentChatRunner,
    private val repository: ConversationRepository,
    private val modelServices: ModelServiceRepository,
    private val preferences: BluetoothVoicePreferences,
) {
    private val mutex = Mutex()

    suspend fun execute(command: String): VoiceAgentResult = mutex.withLock {
        modelServices.awaitReady()
        val selection = modelServices.defaultSelection()
            ?: error("请先配置可用的默认助手模型")
        val encodedSelection = LLMModelSelectionCodec.encode(selection)
        val sessionId = ensureVoiceSession(encodedSelection)
        repository.setConversationModel(sessionId, encodedSelection)
        runner.recreate()

        val accumulator = VoiceResponseAccumulator()
        var confirmationId: String? = null
        runner.send(
            userId = USER_ID,
            sessionId = sessionId,
            text = command,
        ).collect { event ->
            accumulator.accept(event)
            confirmationId = event.confirmationId() ?: confirmationId
        }
        while (confirmationId != null) {
            val currentId = confirmationId ?: break
            confirmationId = null
            runner.respondToToolConfirmation(
                userId = USER_ID,
                sessionId = sessionId,
                confirmationCallId = currentId,
                confirmed = true,
            ).collect { event ->
                accumulator.accept(event)
                confirmationId = event.confirmationId() ?: confirmationId
            }
        }
        repository.refreshConversation(sessionId)
        repository.notifyConversationContentChanged(sessionId)
        VoiceAgentResult(
            sessionId = sessionId,
            responseText = accumulator.result().ifBlank { "任务已完成" },
        )
    }

    private suspend fun ensureVoiceSession(initialModel: String): String {
        preferences.voiceSessionId.value?.takeIf(String::isNotBlank)?.let { saved ->
            if (repository.loadMessages(saved) != null) return saved
        }
        val created = repository.createConversation(initialModel = initialModel, activate = false)
        check(created.isNotBlank()) { "无法创建蓝牙语音会话" }
        preferences.setVoiceSessionId(created)
        return created
    }

    private fun Event.confirmationId(): String? = functionCalls().firstOrNull {
        it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME
    }?.id

    private companion object {
        const val USER_ID = "user-default"
    }
}

internal class VoiceResponseAccumulator {
    private val partial = StringBuilder()
    private var completed = ""

    fun accept(event: Event) {
        if (event.author == "user") return
        val text = event.content?.parts.orEmpty()
            .filter { it.thought != true && it.functionCall == null && it.functionResponse == null }
            .mapNotNull { it.text }
            .joinToString("")
        if (text.isBlank()) return
        if (event.partial) partial.append(text) else completed = text
    }

    fun result(): String = completed.ifBlank { partial.toString() }.trim()
}
