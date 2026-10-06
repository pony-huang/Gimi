package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentExecution
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 发送准备的历史版本选择、配置边界与失败传播，不依赖 Android 或 Agent SDK。 */
class PrepareChatSendUseCaseTest {
    private val resolver = mockk<ConversationSessionResolver>()
    private val repository = mockk<ConversationRepository>()
    private val attachments = mockk<ChatAttachmentRepository>()
    private val agent = mockk<ChatAgentRepository>()
    private val execution = mockk<ChatAgentExecution>()
    private val selection = ModelSelection("service", "group", "model")
    private val configuration = ConversationToolConfiguration(enabledMcpServerIds = setOf("current"))
    private val revisions = MutableStateFlow(mapOf("session" to 2L))
    private val persisted = listOf(Messages.fromUser("persisted history"))
    private val prepare = PrepareChatSendUseCase(resolver, repository, PrepareChatTurnUseCase(attachments), agent)

    init {
        coEvery { resolver.resolveToolConfiguration("session", selection) } returns configuration
        every { repository.conversationContentRevisions } returns revisions
        coEvery { repository.loadMessages("session") } returns persisted
        coEvery { attachments.read("session", emptyList()) } returns emptyList()
        coEvery { agent.createExecution("session", selection, configuration) } returns execution
    }

    @Test
    fun currentCachedHistoryAvoidsPersistenceReadsAndUsesTheResolvedConfiguration() = runBlocking {
        val cached = listOf(Messages.fromUser("cached history"))
        val result = prepare("session", selection, "new input", emptyList(),
            cachedHistory = { ChatHistorySnapshot(cached, 2L) })
        assertEquals(cached, result.turn.messages.dropLast(1))
        assertEquals(configuration, result.toolConfiguration)
        assertSame(execution, result.execution)
        assertEquals(2L, result.contentRevision)
        coVerify(exactly = 0) { repository.loadMessages(any()) }
    }

    @Test
    fun staleCachedHistoryReloadsPersistence() = runBlocking {
        val result = prepare("session", selection, "new input", emptyList(),
            cachedHistory = { ChatHistorySnapshot(emptyList(), 1L) })
        assertEquals(persisted, result.turn.messages.dropLast(1))
        coVerify(exactly = 1) { repository.loadMessages("session") }
    }

    @Test
    fun cachedHistoryIsReadAfterConfigurationResolution() = runBlocking {
        val calls = mutableListOf<String>()
        coEvery { resolver.resolveToolConfiguration("session", selection) } coAnswers {
            calls += "configuration"
            configuration
        }
        prepare("session", selection, "new input", emptyList(), cachedHistory = {
            calls += "cached history"
            ChatHistorySnapshot(persisted, 2L)
        })
        assertEquals(listOf("configuration", "cached history"), calls)
    }

    @Test
    fun configurationFailurePreventsInputArchivalAndExecutionCreation() = runBlocking {
        val failure = IOException("configuration unavailable")
        coEvery { resolver.resolveToolConfiguration("session", selection) } throws failure
        val actual = runCatching { prepare("session", selection, "new input", emptyList()) }.exceptionOrNull()
        assertSame(failure, actual)
        coVerify(exactly = 0) { attachments.read(any(), any()) }
        coVerify(exactly = 0) { agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun inputPreparationFailurePreventsExecutionCreation() = runBlocking {
        val failure = IOException("attachment unreadable")
        coEvery { attachments.read("session", emptyList()) } throws failure
        val actual = runCatching { prepare("session", selection, "new input", emptyList()) }.exceptionOrNull()
        assertSame(failure, actual)
        coVerify(exactly = 0) { agent.createExecution(any(), any(), any()) }
    }

    @Test
    fun cancellationPropagatesWithoutPreparingTheInput() = runBlocking {
        val cancelled = CancellationException("cancelled")
        coEvery { resolver.resolveToolConfiguration("session", selection) } throws cancelled
        val actual = runCatching { prepare("session", selection, "new input", emptyList()) }.exceptionOrNull()
        assertSame(cancelled, actual)
        coVerify(exactly = 0) { attachments.read(any(), any()) }
        coVerify(exactly = 0) { agent.createExecution(any(), any(), any()) }
    }
}
