package github.ponyhuang.asssistantai.voice

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.FunctionCall
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.domain.conversation.repository.ConversationRepository
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class VoiceAgentResult(
    val sessionId: String,
    val responseText: String,
)

data class VoiceToolConfirmation(
    val callId: String,
    val toolName: String,
)

@Singleton
class VoiceAgentTaskExecutor @Inject constructor(
    @VoiceAgentRunner private val runner: AgentChatRunner,
    private val repository: ConversationRepository,
    private val modelServices: ModelCatalogRepository,
    private val preferences: BluetoothVoicePreferences,
    private val agentRuntimeGate: AgentRuntimeGate,
) {
    private val mutex = Mutex()

    suspend fun execute(
        command: String,
        confirmTool: suspend (VoiceToolConfirmation) -> Boolean,
    ): VoiceAgentResult = mutex.withLock {
        val lease = agentRuntimeGate.acquire(AgentTaskSource.BLUETOOTH_VOICE)
        try {
            modelServices.awaitReady()
            val selection = defaultSelection()
                ?: error("请先配置可用的默认助手模型")
            val encodedSelection = ModelSelectionCodec.encode(selection)
            val sessionId = ensureVoiceSession(encodedSelection)
            repository.setConversationModel(sessionId, encodedSelection)
            runner.recreate()

            val accumulator = VoiceResponseAccumulator()
            val pendingConfirmations = ArrayDeque<VoiceToolConfirmation>()
            val seenConfirmationIds = mutableSetOf<String>()
            val approvedTools = mutableSetOf<String>()
            runner.send(
                userId = USER_ID,
                sessionId = sessionId,
                text = command,
            ).collect { event ->
                accumulator.accept(event)
                event.enqueueConfirmations(pendingConfirmations, seenConfirmationIds)
            }
            while (pendingConfirmations.isNotEmpty()) {
                lease.updatePhase(AgentTaskPhase.WAITING_FOR_CONFIRMATION)
                val request = pendingConfirmations.removeFirst()
                val confirmed = request.toolName in approvedTools || confirmTool(request)
                if (confirmed) {
                    approvedTools += request.toolName
                } else {
                    approvedTools.clear()
                }
                lease.updatePhase(AgentTaskPhase.GENERATING)
                runner.respondToToolConfirmation(
                    userId = USER_ID,
                    sessionId = sessionId,
                    confirmationCallId = request.callId,
                    confirmed = confirmed,
                ).collect { event ->
                    accumulator.accept(event)
                    event.enqueueConfirmations(pendingConfirmations, seenConfirmationIds)
                }
            }
            repository.refreshConversation(sessionId)
            repository.notifyConversationContentChanged(sessionId)
            VoiceAgentResult(
                sessionId = sessionId,
                responseText = accumulator.result().ifBlank { "任务已完成" },
            )
        } finally {
            lease.release()
        }
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

    private fun defaultSelection(): ModelSelection? {
        val services = modelServices.currentServices()
        val saved = modelServices.currentAssistantSelection()
        if (saved != null && services.isUsable(saved)) return saved
        return services.asSequence()
            .filter { it.isEnabled && it.apiKey.isNotBlank() }
            .flatMap { service -> service.groups.asSequence().flatMap { group ->
                group.models.asSequence()
                    .filter { !it.isStt && !it.isTts }
                    .map { ModelSelection(service.id, group.id, it.id) }
            } }
            .firstOrNull()
    }

    private fun Event.enqueueConfirmations(
        target: ArrayDeque<VoiceToolConfirmation>,
        seenIds: MutableSet<String>,
    ) {
        functionCalls().filter {
            it.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME
        }.forEach { call ->
            val callId = call.id ?: return@forEach
            if (!seenIds.add(callId)) return@forEach
            val original = call.args[FunctionCall.ORIGINAL_FUNCTION_CALL_KEY] as? Map<*, *>
                ?: return@forEach
            val toolName = original["name"] as? String ?: return@forEach
            target += VoiceToolConfirmation(callId = callId, toolName = toolName)
        }
    }

    private companion object {
        const val USER_ID = "user-default"
    }
}

private fun List<ModelService>.isUsable(selection: ModelSelection): Boolean {
    val service = firstOrNull { it.id == selection.serviceId } ?: return false
    if (!service.isEnabled || service.apiKey.isBlank()) return false
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return false
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return false
    return !model.isStt && !model.isTts
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
