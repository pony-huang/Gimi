package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.*
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.data.agent.*
import github.ponyhuang.gimi.data.agent.conversation.AdkChatAgentRepository
import github.ponyhuang.gimi.domain.a2ui.*
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** 真实 Runner 的异步交付、取消与归档回归，避免主 Agent 等待及后台付费调用泄漏。 */
class A2uiAsyncCreateTest {
    private val key = SessionKey(AgentChatRunner.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, "chat")
    private val protocol = object : A2uiProtocol {
        override suspend fun validate(document: A2uiDocument, capabilities: A2uiCapabilities) = Unit
        override fun replay(records: List<A2uiRecord>) = records.filterIsInstance<A2uiRecord.Revision>().map { it.document }
    }

    @Test
    fun parentAnswerPrecedesGenerationAndResultIsArchived() = runTest(timeout = 15.seconds) {
        val answerSeen = CompletableDeferred<Unit>()
        val sessions = InMemorySessionService()
        val execution = execution(sessions) { answerSeen.await() }
        val statuses = mutableListOf<A2uiGenerationStatus>()
        execution.send("show image", emptyList()).collect { event ->
            if (event.parts.any { it.text == "parent answer" }) answerSeen.complete(Unit)
            event.functionResponses.forEach { response ->
                response.a2uiTask?.let { statuses += it.status }
                if (response.a2uiDocument != null) assertTrue(answerSeen.isCompleted)
            }
        }
        assertEquals(listOf(A2uiGenerationStatus.PENDING, A2uiGenerationStatus.COMPLETED), statuses)
        assertNotNull(A2uiToolResult.extract(A2uiToolResult.TOOL_NAME, archivedResult(sessions)))
    }

    @Test
    fun stoppingAfterParentAnswerCancelsWorkerAndArchivesCancellation() = runTest(timeout = 15.seconds) {
        val sessions = InMemorySessionService()
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val execution = execution(sessions) {
            started.complete(Unit)
            try { awaitCancellation() } finally { stopped.complete(Unit) }
        }
        val job = launch {
            execution.send("show image", emptyList()).collect { event ->
                if (event.parts.any { it.text == "parent answer" }) {
                    started.await()
                    throw CancellationException("user stopped")
                }
            }
        }
        job.join()
        assertTrue(stopped.isCompleted)
        assertEquals(A2uiGenerationStatus.CANCELLED, A2uiGenerationTaskCodec.extract(archivedResult(sessions))!!.status)
    }

    @Test
    fun networkFailureDoesNotFailParentOrRetryPaidGeneration() = runTest(timeout = 15.seconds) {
        var calls = 0
        val sessions = InMemorySessionService()
        val execution = execution(sessions) { calls++; error("network") }
        val events = execution.send("show image", emptyList()).toList()
        assertTrue(events.flatMap { it.parts }.any { it.text == "parent answer" })
        assertEquals(A2uiGenerationStatus.FAILED, events.flatMap { it.functionResponses }.last().a2uiTask!!.status)
        assertEquals(1, calls)
    }

    @Test
    fun sameTurnDeleteWaitsForCreationAndCannotBeUndoneByLateDelivery() = runTest(timeout = 15.seconds) {
        val sessions = InMemorySessionService()
        val execution = execution(sessions, mutation = "delete") {}

        val events = execution.send("show then delete", emptyList()).toList()

        val documents = events.flatMap { it.functionResponses }.mapNotNull { it.a2uiDocument }
        assertTrue(documents.isNotEmpty())
        assertTrue(documents.all { it.deleted })
        assertTrue(A2uiToolResult.extract(A2uiToolResult.TOOL_NAME, archivedResult(sessions))!!.deleted)
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun archivedResult(sessions: InMemorySessionService): Map<String, Any?> {
        val event = sessions.getSession(key)!!.events.last { it.author == A2uiGenerationTaskCodec.AUTHOR }
        assertEquals(Role.SYSTEM, event.content!!.role)
        assertTrue(event.functionResponses().isEmpty())
        return event.customMetadata!![A2uiGenerationTaskCodec.RESULT_KEY] as Map<String, Any?>
    }

    private suspend fun execution(sessions: InMemorySessionService, mutation: String? = null, before: suspend () -> Unit): github.ponyhuang.gimi.domain.conversation.repository.ChatAgentExecution {
        var calls = 0
        val worker = object : Model {
            override val name = "worker"
            override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
                before()
                emit(LlmResponse(content = Content(role = Role.MODEL, parts = listOf(Part(functionCall = FunctionCall(
                    name = "render_a2ui", args = mapOf("components" to """[{"id":"root","component":"Image","url":"https://fixture.invalid/image.png"}]""", "data" to "{}"),
                ))))))
            }
        }
        val parent = object : Model {
            override val name = "parent"
            override fun generateContent(request: LlmRequest, stream: Boolean) = flow {
                val step = calls++
                val parts = if (step == 0) listOf(
                    Part(functionCall = FunctionCall(id = "create", name = "generate_a2ui", args = mapOf(
                        "intent" to "create", "changes" to "image URL", "component_names" to listOf("Image"),
                    ))),
                ) else if (step == 1 && mutation != null) {
                    val response = request.contents.flatMap { it.parts }.mapNotNull { it.functionResponse }.last().response
                    val payload = (response["result"] as? Map<*, *>) ?: response
                    listOf(Part(functionCall = FunctionCall(id = "mutate", name = "generate_a2ui", args = mapOf(
                        "intent" to mutation, "target_surface_id" to payload["surfaceId"],
                    ))))
                } else listOf(Part(text = "parent answer"))
                emit(LlmResponse(content = Content(role = Role.MODEL, parts = parts)))
            }
        }
        val config = ModelConfig("service", ApiProtocol.OpenaiChatcompletions, "fixture", "fixture-key", "https://fixture.invalid")
        val access = object : ToolAccessRepository {
            override val defaultToolAccessMode = MutableStateFlow(ToolAccessMode.ALWAYS_AVAILABLE)
            override fun setDefaultToolAccessMode(mode: ToolAccessMode) { defaultToolAccessMode.value = mode }
        }
        val runner = AgentChatRunner(factory = {
            AgentRuntime(LlmAgent(name = "Assistant", model = parent,
                tools = listOf(GenerateA2uiTool(A2uiGeneration(worker, protocol), protocol))), config.toRuntimeMetadata())
        }, sessionService = sessions, artifactService = null, memoryService = null, toolAccessRepository = access)
        sessions.createSession(key)
        return AdkChatAgentRepository(runner).createExecution("chat", ModelSelection("service", "group", "fixture"),
            a2uiCapabilities = A2uiCapabilities("catalog", "{}"))
    }
}
