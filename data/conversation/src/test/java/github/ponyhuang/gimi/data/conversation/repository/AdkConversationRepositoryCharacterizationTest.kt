package github.ponyhuang.gimi.data.conversation.repository

import android.content.Context
import android.util.Log
import app.cash.turbine.test
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.gimi.data.conversation.local.ConversationMetadataDao
import github.ponyhuang.gimi.data.conversation.local.ConversationMetadataEntity
import github.ponyhuang.gimi.data.conversation.local.ConversationToolConfigurationCodec
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AdkConversationRepositoryCharacterizationTest {

    private val sessionService = mockk<SessionService>()
    private val metadataDao = mockk<ConversationMetadataDao>(relaxed = true)
    private val context = mockk<Context> {
        every { getString(any()) } returns "新对话"
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun contentRevisionsRetainEverySessionBeforeSubscription() = runTest {
        val repository = repository()
        repository.notifyConversationContentChanged("")
        repository.notifyConversationContentChanged("session-1")
        repository.notifyConversationContentChanged("session-2")
        repository.notifyConversationContentChanged("session-1")
        repository.conversationContentRevisions.test {
            assertEquals(mapOf("session-1" to 2L, "session-2" to 1L), awaitItem())
        }
    }

    @Test
    fun createConversation_returnsBlank_whenSessionStorageFails() = runTest {
        coEvery { sessionService.createSession(any()) } throws IllegalStateException("storage unavailable")

        assertEquals("", repository().createConversation())
    }

    @Test
    fun refresh_propagatesCancellation() = runTest {
        coEvery {
            sessionService.listSessions(appName = any(), userId = any())
        } throws CancellationException("cancelled")

        try {
            repository().refresh()
            throw AssertionError("CancellationException was swallowed")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    @Test
    fun conversationToolConfiguration_readsPersistedSnapshot() = runTest {
        val expected = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("mcp-1"),
        )
        coEvery { metadataDao.get("session-1") } returns ConversationMetadataEntity(
            sessionId = "session-1",
            toolConfigurationJson = ConversationToolConfigurationCodec.encode(expected),
        )

        assertEquals(expected, repository().conversationToolConfiguration("session-1"))
    }

    @Test
    fun setConversationToolConfiguration_persistsSnapshotWithoutReplacingOtherMetadata() = runTest {
        val configuration = ConversationToolConfiguration(
            enabledMcpServerIds = setOf("mcp-1"),
        )

        repository().setConversationToolConfiguration("session-1", configuration)

        coVerify {
            metadataDao.setToolConfiguration(
                "session-1",
                match { payload ->
                    ConversationToolConfigurationCodec.decode(payload) == configuration
                },
            )
        }
    }

    @Test
    fun deleteConversationRemovesStorageMetadataAndContentRevision() = runTest {
        coEvery { sessionService.deleteSession(any()) } returns Unit
        coEvery { sessionService.listSessions(appName = any(), userId = any()) } returns mockk {
            every { sessions } returns emptyList()
        }
        val repository = repository()
        repository.notifyConversationContentChanged("session-1")
        repository.deleteConversation("session-1")
        coVerify(exactly = 1) { sessionService.deleteSession(match { it.id == "session-1" }) }
        coVerify(exactly = 1) { metadataDao.delete("session-1") }
        assertEquals(emptyMap<String, Long>(), repository.conversationContentRevisions.value)
    }

    @Test
    fun deleteConversationPropagatesStorageFailureWithoutDeletingMetadata() = runTest {
        val error = java.io.IOException("storage unavailable")
        coEvery { sessionService.deleteSession(any()) } throws error
        val repository = repository()
        repository.notifyConversationContentChanged("session-1")
        try {
            repository.deleteConversation("session-1")
            throw AssertionError("Deletion failure was swallowed")
        } catch (actual: java.io.IOException) {
            assertEquals(error, actual)
        }
        coVerify(exactly = 0) { metadataDao.delete(any()) }
        assertEquals(mapOf("session-1" to 1L), repository.conversationContentRevisions.value)
    }

    @Test
    fun deleteConversationPropagatesMetadataFailure() = runTest {
        coEvery { sessionService.deleteSession(any()) } returns Unit
        val error = IllegalStateException("metadata unavailable")
        coEvery { metadataDao.delete("session-1") } throws error
        try {
            repository().deleteConversation("session-1")
            throw AssertionError("Metadata failure was swallowed")
        } catch (actual: IllegalStateException) {
            assertEquals(error, actual)
        }
    }

    @Test
    fun deleteConversationPropagatesCancellationWithoutDeletingMetadata() = runTest {
        coEvery { sessionService.deleteSession(any()) } throws CancellationException("cancelled")
        try {
            repository().deleteConversation("session-1")
            throw AssertionError("Cancellation was swallowed")
        } catch (_: CancellationException) {
            // 取消不能转换成删除成功。
        }
        coVerify(exactly = 0) { metadataDao.delete(any()) }
    }

    private fun repository() = AdkConversationRepository(
        appName = "test-app",
        userId = "test-user",
        sessionService = sessionService,
        metadataDao = metadataDao,
        context = context,
    )
}
