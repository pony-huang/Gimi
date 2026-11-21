package github.ponyhuang.gimi.data.conversation.export

import android.content.ContentResolver
import android.content.Context
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.events.Event
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.State
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.conversation.conversationExportDirectorySpec
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportSession
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@OptIn(FrameworkInternalApi::class)
class AndroidConversationExportRepositoryTest {
    @get:Rule val files = TemporaryFolder()
    private val service = mockk<SessionService>()
    private val resolver = mockk<ContentResolver>()
    private val context = mockk<Context>()
    private val output = ByteArrayOutputStream()

    private fun repository(): AndroidConversationExportRepository {
        val storageRegistry = mockk<StorageRegistry>()
        every { storageRegistry.resolve(conversationExportDirectorySpec.id, create = true) } returns files.root
        every { context.contentResolver } returns resolver
        every { resolver.openOutputStream(any(), "wt") } returns output
        return AndroidConversationExportRepository(context, service, storageRegistry)
    }

    private fun session(userId: String, id: String, events: List<Event> = emptyList()) = Session(
        key = SessionKey(AgentSessionIdentity.APP_NAME, userId, id),
        state = State(mapOf("nested" to mapOf("list" to listOf("中文\n第二行", 42)))),
        events = events.toMutableList(),
    )

    @Test
    fun singleFileKeepsRawEventsToolPayloadAndStateOnExactlyOneLine() = runTest {
        val repository = repository()
        val payload = "完整工具参数与结果".repeat(200)
        val event = Event(
            id = "event",
            author = "assistant",
            content = Content(parts = listOf(
                Part(text = "思考\n内容", thought = true, thoughtSignature = byteArrayOf(1, 2, 3)),
                Part(functionCall = FunctionCall("tool", mapOf("nested" to listOf(payload)), "call")),
                Part(functionResponse = FunctionResponse("tool", mapOf("result" to payload), "call")),
            )),
            branch = "agent.child",
            errorMessage = "异常\n详情",
        )
        val stored = session(AgentSessionIdentity.DEFAULT_USER_ID, "one", listOf(event, Event(id = "empty", author = "assistant")))
        coEvery { service.getSession(any(), any()) } returns stored
        val request = repository.prepare(ConversationExportSession(AgentSessionIdentity.DEFAULT_USER_ID, "one"))
        repository.export(request, "content://export")

        val lines = output.toString(Charsets.UTF_8.name()).lineSequence().filter(String::isNotEmpty).toList()
        assertEquals(1, lines.size)
        val document = Json.parseToJsonElement(lines.single()).jsonObject
        assertEquals(setOf("key", "state", "events", "lastUpdateTime"), document.keys)
        assertEquals("one", document.getValue("key").jsonObject.getValue("id").jsonPrimitive.content)
        val restored = adkJson.decodeFromJsonElement(Event.serializer(), document.getValue("events").jsonArray.first())
        assertEquals(event, restored)
        assertEquals(2, document.getValue("events").jsonArray.size)
        assertTrue(document.getValue("state").toString().contains("中文"))
        assertTrue(request.fileName.matches(Regex("\\d{8}_\\d{6}_one\\.jsonl")))
        assertTrue(files.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun chatbotPartitionSessionIsReadFromItsOwnUserId() = runTest {
        val repository = repository()
        val stored = session(AgentSessionIdentity.CHATBOT_USER_ID, "bot")
        coEvery { service.getSession(any(), any()) } answers {
            // 助手分区里没有同名会话：只有按 user-chatbot 组键才能读到。
            if (firstArg<SessionKey>().userId == AgentSessionIdentity.CHATBOT_USER_ID) stored else null
        }
        val request = repository.prepare(ConversationExportSession(AgentSessionIdentity.CHATBOT_USER_ID, "bot"))
        repository.export(request, "content://export")

        val document = Json.parseToJsonElement(output.toString(Charsets.UTF_8.name()).trim()).jsonObject
        assertEquals(
            AgentSessionIdentity.CHATBOT_USER_ID,
            document.getValue("key").jsonObject.getValue("userId").jsonPrimitive.content,
        )
        assertEquals("application/x-ndjson", request.mimeType)
        coVerify {
            service.getSession(SessionKey(AgentSessionIdentity.APP_NAME, AgentSessionIdentity.CHATBOT_USER_ID, "bot"))
        }
    }

    @Test
    fun missingSessionAbortsBeforeOpeningDestinationAndRemovesStagedFile() = runTest {
        val repository = repository()
        coEvery { service.getSession(any(), any()) } returns null
        val request = repository.prepare(ConversationExportSession(AgentSessionIdentity.DEFAULT_USER_ID, "missing"))
        try {
            repository.export(request, "content://export")
            fail("Expected missing session failure")
        } catch (_: IOException) {
            verify(exactly = 0) { resolver.openOutputStream(any(), any()) }
            assertTrue(files.root.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun cancellationPropagatesWithoutCreatingOutputOrLeavingStagedData() = runTest {
        val repository = repository()
        coEvery { service.getSession(any(), any()) } throws CancellationException("cancelled")
        val request = repository.prepare(ConversationExportSession(AgentSessionIdentity.DEFAULT_USER_ID, "one"))
        try {
            repository.export(request, "content://export")
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            verify(exactly = 0) { resolver.openOutputStream(any(), any()) }
            assertTrue(files.root.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun documentWriteFailurePropagatesAndRemovesStagedData() = runTest {
        val repository = repository()
        coEvery { service.getSession(any(), any()) } returns session(AgentSessionIdentity.DEFAULT_USER_ID, "one")
        every { resolver.openOutputStream(any(), "wt") } throws IOException("provider failed")
        try {
            repository.export(
                repository.prepare(ConversationExportSession(AgentSessionIdentity.DEFAULT_USER_ID, "one")),
                "content://export",
            )
            fail("Expected write failure")
        } catch (_: IOException) {
            assertTrue(files.root.listFiles().orEmpty().isEmpty())
        }
    }
}
