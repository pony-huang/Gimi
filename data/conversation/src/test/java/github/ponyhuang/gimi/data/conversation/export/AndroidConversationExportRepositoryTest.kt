package github.ponyhuang.gimi.data.conversation.export

import android.content.ContentResolver
import android.content.Context
import com.google.adk.kt.annotations.FrameworkInternalApi
import com.google.adk.kt.events.Event
import com.google.adk.kt.serialization.adkJson
import com.google.adk.kt.sessions.ListSessionsResponse
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
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun session(id: String, events: List<Event> = emptyList()) = Session(
        key = SessionKey(AgentSessionIdentity.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, id),
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
        val stored = session("one", listOf(event, Event(id = "empty", author = "assistant")))
        coEvery { service.getSession(any(), any()) } returns stored
        val request = requireNotNull(repository.prepare("one"))
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
        coVerify(exactly = 0) { service.listSessions(any(), any()) }
        assertTrue(files.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun multipleSessionsProduceSeparateSafeJsonlEntriesInAZip() = runTest {
        val repository = repository()
        val sessions = listOf(session("first"), session("../second"))
        coEvery { service.listSessions(any(), any()) } returns ListSessionsResponse(sessions)
        coEvery { service.getSession(any(), any()) } answers {
            sessions.single { it.key.id == firstArg<SessionKey>().id }
        }
        val request = requireNotNull(repository.prepare(null))
        assertEquals("application/zip", request.mimeType)
        repository.export(request, "content://export")
        val names = mutableListOf<String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                assertFalse(entry.name.contains('/'))
                val document = Json.parseToJsonElement(zip.readBytes().toString(Charsets.UTF_8)).jsonObject
                assertEquals(0, document.getValue("events").jsonArray.size)
            }
        }
        assertEquals(2, names.size)
        assertTrue(names.all { it.startsWith(request.timestamp + "_") && it.endsWith(".jsonl") })
        assertTrue(files.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun emptyListDoesNotCreateAnExportAndOneSessionInAllUsesJsonl() = runTest {
        val repository = repository()
        coEvery { service.listSessions(any(), any()) } returns ListSessionsResponse(emptyList())
        assertNull(repository.prepare(null))
        coEvery { service.listSessions(any(), any()) } returns ListSessionsResponse(listOf(session("only")))
        val request = requireNotNull(repository.prepare(null))
        assertEquals("application/x-ndjson", request.mimeType)
        assertTrue(request.allSessions)
        verify(exactly = 0) { resolver.openOutputStream(any(), any()) }
    }

    @Test
    fun missingSessionAbortsBeforeOpeningDestinationAndRemovesStagedFile() = runTest {
        val repository = repository()
        coEvery { service.getSession(any(), any()) } returns null
        val request = requireNotNull(repository.prepare("missing"))
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
        val request = requireNotNull(repository.prepare("one"))
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
        coEvery { service.getSession(any(), any()) } returns session("one")
        every { resolver.openOutputStream(any(), "wt") } throws IOException("provider failed")
        try {
            repository.export(requireNotNull(repository.prepare("one")), "content://export")
            fail("Expected write failure")
        } catch (_: IOException) {
            assertTrue(files.root.listFiles().orEmpty().isEmpty())
        }
    }
}
