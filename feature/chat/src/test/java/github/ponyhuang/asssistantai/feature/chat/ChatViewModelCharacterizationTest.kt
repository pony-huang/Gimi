package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.core.testing.MainDispatcherRule
import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunEvent
import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunPart
import github.ponyhuang.asssistantai.domain.conversation.model.MessageRole
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ConversationRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCharacterizationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun send_preservesOptimisticUserMessage_andCompletesAssistantPartial() = runTest {
        val fixture = fixture(configured = true)
        fixture.viewModel.send("你好")

        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(MessageRole.User, state.messages[0].role)
        assertEquals("你好", state.messages[0].textParts.single().text)
        assertEquals(MessageRole.Assistant, state.messages[1].role)
        assertEquals("回复", state.messages[1].textParts.single().text)
        assertFalse(state.messages[1].partial)
        assertFalse(state.isAgentRunning)
        coVerify { fixture.conversations.refreshConversation("session-1") }
    }

    @Test
    fun send_withoutConfiguredChatModel_emitsActionableNoticeWithoutRunningAgent() = runTest {
        val fixture = fixture(configured = false)

        fixture.viewModel.send("你好")
        advanceUntilIdle()

        assertEquals(ChatNotice.ConfigureChatModel, fixture.viewModel.uiState.value.notice)
        assertFalse(fixture.viewModel.uiState.value.isAgentRunning)
        coVerify(exactly = 0) { fixture.agent.send(any(), any(), any()) }
    }

    @Test
    fun consumeNotice_clearsOneShotEffect() = runTest {
        val fixture = fixture(configured = false)
        fixture.viewModel.send("你好")

        fixture.viewModel.consumeNotice()

        assertEquals(null, fixture.viewModel.uiState.value.notice)
    }

    private fun fixture(configured: Boolean): Fixture {
        val selection = ModelSelection("service", "chat", "model")
        val services = if (configured) listOf(service()) else emptyList()
        val catalog = mockk<ModelCatalogRepository>(relaxed = true) {
            every { observeServices() } returns MutableStateFlow(services)
            every { observeLoadState() } returns MutableStateFlow(CatalogLoadState.Ready)
            every { currentServices() } returns services
            every { currentAssistantSelection() } returns selection.takeIf { configured }
            coEvery { awaitReady() } returns Unit
        }
        val conversations = mockk<ConversationRepository>(relaxed = true) {
            every { this@mockk.conversations } returns MutableStateFlow(emptyList())
            every { conversationContentUpdates } returns MutableSharedFlow()
            coEvery { createConversation(any(), any()) } returns "session-1"
            coEvery { activateConversation("session-1", any()) } returns ModelSelectionCodec.encode(selection)
        }
        val agent = mockk<ChatAgentRepository>(relaxed = true) {
            coEvery { activateModel(any()) } returns Result.success(Unit)
            coEvery { send(any(), any(), any()) } returns flowOf(
                event(partial = true, turnComplete = false),
                event(partial = false, turnComplete = true),
            )
        }
        val display = mockk<ChatDisplayRepository> {
            every { showToolActivity } returns MutableStateFlow(true)
        }
        val recognition = mockk<SpeechRecognitionRepository>(relaxed = true) {
            every { availability } returns MutableStateFlow(false)
        }
        val playback = mockk<SpeechPlaybackRepository>(relaxed = true) {
            every { state } returns MutableStateFlow(SpeechPlaybackState())
            every { errors } returns MutableSharedFlow()
        }
        val attachments = mockk<ChatAttachmentRepository> {
            coEvery { read(any()) } returns emptyList()
        }
        return Fixture(
            viewModel = ChatViewModel(
                runner = agent,
                repository = conversations,
                modelServices = catalog,
                chatDisplayPreferences = display,
                speechRecognitionRepository = recognition,
                speechPlaybackController = playback,
                attachments = attachments,
            ),
            conversations = conversations,
            agent = agent,
        )
    }

    private fun event(partial: Boolean, turnComplete: Boolean) = ChatRunEvent(
        id = "event-1",
        invocationId = "invocation-1",
        author = "assistant",
        parts = listOf(ChatRunPart(text = "回复")),
        functionCalls = emptyList(),
        functionResponses = emptyList(),
        partial = partial,
        turnComplete = turnComplete,
        errorCode = null,
        errorMessage = null,
        timestamp = 1L,
    )

    private fun service() = ModelService(
        id = "service",
        name = "Service",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.test",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "",
        groups = listOf(
            ModelGroup(
                id = "chat",
                name = "Chat",
                models = listOf(Model(id = "model", name = "Model")),
            ),
        ),
    )

    private data class Fixture(
        val viewModel: ChatViewModel,
        val conversations: ConversationRepository,
        val agent: ChatAgentRepository,
    )
}
