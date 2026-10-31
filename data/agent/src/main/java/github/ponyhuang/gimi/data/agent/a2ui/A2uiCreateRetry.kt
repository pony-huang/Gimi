package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.domain.a2ui.*
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 重试复用已归档的事实和 surface，不启动主 Agent，也不执行任何业务工具。 */
class A2uiCreateRetry @Inject constructor(
    private val models: AgentLLMModelFactory,
    private val protocol: A2uiProtocol,
    private val sessions: SessionService,
) {
    fun retry(sessionId: String, selection: ModelSelection, callId: String, capabilities: A2uiCapabilities) = flow {
        val key = SessionKey(AgentSessionIdentity.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, sessionId)
        val session = requireNotNull(sessions.getSession(key))
        @Suppress("UNCHECKED_CAST")
        val task = session.events.asReversed().firstNotNullOfOrNull { event ->
            val responses = event.functionResponses().map { it.response } + listOfNotNull(
                event.customMetadata?.get(A2uiGenerationTaskCodec.RESULT_KEY) as? Map<String, Any?>,
            )
            responses.mapNotNull(A2uiGenerationTaskCodec::extract).firstOrNull { it.callId == callId }
        } ?: error("A2UI task does not exist")
        require(task.status != A2uiGenerationStatus.COMPLETED) { "A2UI task already completed" }
        suspend fun persist(result: Map<String, Any>): Event? {
            val current = sessions.getSession(key) ?: return null
            val snapshot = requireNotNull(A2uiGenerationTaskCodec.extract(result))
            val event = Event(
                author = A2uiGenerationTaskCodec.AUTHOR, invocationId = task.invocationId,
                content = Content(role = Role.SYSTEM, parts = listOf(Part(
                    text = "A2UI surface ${task.surfaceId}: ${snapshot.status.name.lowercase()}.",
                ))),
                customMetadata = mapOf(A2uiGenerationTaskCodec.RESULT_KEY to result),
            )
            sessions.appendEvent(current, event)
            return event
        }
        persist(taskResult(task.copy(status = A2uiGenerationStatus.PENDING)))?.let { emit(it) }
        val result = try {
            val generation = A2uiGeneration(models.createModel(models.selectModelConfig(selection)), protocol)
            val document = generation.generate(task.surfaceId, capabilities, "Requested UI: ${task.changes}",
                requireWeatherComponent = requestedWeatherComponent(task.changes, task.componentNames),
                requestedComponents = task.componentNames)
            taskResult(task.copy(status = A2uiGenerationStatus.COMPLETED)) +
                (A2uiToolResult.DOCUMENT_KEY to A2uiToolResult.encode(document))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { persist(taskResult(task.copy(status = A2uiGenerationStatus.CANCELLED))) }
            throw cancelled
        } catch (_: Exception) {
            taskResult(task.copy(status = A2uiGenerationStatus.FAILED))
        }
        persist(result)?.let { emit(it) }
    }.flowOn(Dispatchers.IO)
}
