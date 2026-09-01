package github.ponyhuang.gimi.data.memory

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class Mem0MemoryServiceTest {
    private val server = MockWebServer()
    private lateinit var settings: FakeMemorySettingsRepository
    private lateinit var status: DefaultMemoryRuntimeStatus
    private lateinit var service: Mem0MemoryService

    @Before
    fun setUp() {
        server.start()
        settings = FakeMemorySettingsRepository(MemoryConfiguration(mem0Enabled = true, apiKey = "mem0-key"))
        status = DefaultMemoryRuntimeStatus()
        service = Mem0MemoryService(
            api = Mem0ApiClient(
                httpClient = OkHttpClient(),
                settingsRepository = settings,
                baseUrl = server.url("/").toString().removeSuffix("/"),
            ),
            runtimeStatus = status,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search sends scoped V3 request and maps results`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"results":[{"id":"m1","memory":"Likes tea","metadata":{"author":"user"},"created_at":"2026-08-29T00:00:00Z","score":0.9}]}
                """.trimIndent(),
            ),
        )

        val response = service.searchMemory("Gimi", "user-default", "favorite drink")

        val request = server.takeRequest()
        assertEquals("/v3/memories/search/", request.path)
        assertEquals("Token mem0-key", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("favorite drink", body.getValue("query").jsonPrimitive.content)
        assertEquals("user-default", body.getValue("filters").jsonObject.getValue("AND").jsonArray[0].jsonObject.getValue("user_id").jsonPrimitive.content)
        assertEquals("Gimi", body.getValue("filters").jsonObject.getValue("AND").jsonArray[1].jsonObject.getValue("app_id").jsonPrimitive.content)
        assertEquals("5", body.getValue("top_k").jsonPrimitive.content)
        assertEquals("m1", response.memories.single().id)
        assertEquals("Likes tea", response.memories.single().content.parts.single().text)
        assertEquals("user", response.memories.single().author)
    }

    @Test
    fun `add session sends user and model text as Mem0 roles`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"event_id":"e1","status":"PENDING"}"""))
        val session = Session(
            key = SessionKey("Gimi", "user-default", "session-1"),
            events = mutableListOf(
                Event(author = "user", content = Content(role = Role.USER, parts = listOf(Part(text = "Hello")))),
                Event(author = "Assistant", content = Content(role = Role.MODEL, parts = listOf(Part(text = "Hi")))),
                Event(author = "Assistant", content = Content(role = Role.MODEL, parts = listOf(Part()))),
            ),
        )

        service.addSessionToMemory(session)

        val request = server.takeRequest()
        assertEquals("/v3/memories/add/", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("user-default", body.getValue("user_id").jsonPrimitive.content)
        assertEquals("Gimi", body.getValue("app_id").jsonPrimitive.content)
        val messages = body.getValue("messages").jsonArray
        assertEquals(listOf("user", "assistant"), messages.map { it.jsonObject.getValue("role").jsonPrimitive.content })
        assertEquals(listOf("Hello", "Hi"), messages.map { it.jsonObject.getValue("content").jsonPrimitive.content })
    }

    @Test
    fun `search failure returns empty memories and reports failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"invalid token"}"""))

        val response = service.searchMemory("Gimi", "user-default", "query")

        assertTrue(response.memories.isEmpty())
        assertEquals(MemoryOperation.SEARCH, status.lastFailure)
    }

    @Test
    fun `routing service uses selected backend`() = runTest {
        val local = RecordingMemoryService()
        val mem0 = RecordingMemoryService()
        val routing = RoutingMemoryService(settings, local, mem0)

        settings.mutableConfiguration.value = MemoryConfiguration()
        routing.searchMemory("Gimi", "user", "local")
        settings.mutableConfiguration.value = MemoryConfiguration(mem0Enabled = true, apiKey = "key")
        routing.searchMemory("Gimi", "user", "cloud")

        assertEquals(listOf("local"), local.queries)
        assertEquals(listOf("cloud"), mem0.queries)
    }

    @Test
    fun `routing service no-ops search and writes when memory disabled`() = runTest {
        val local = RecordingMemoryService()
        val mem0 = RecordingMemoryService()
        val routing = RoutingMemoryService(settings, local, mem0)

        settings.mutableConfiguration.value = MemoryConfiguration(memoryEnabled = false)
        val session = Session(
            key = SessionKey("Gimi", "user", "session-1"),
            events = mutableListOf(
                Event(author = "user", content = Content(role = Role.USER, parts = listOf(Part(text = "Hello")))),
            ),
        )

        val response = routing.searchMemory("Gimi", "user", "query")
        routing.addSessionToMemory(session)
        routing.addEventsToMemory("Gimi", "user", listOf(Event(author = "user")), sessionId = "session-1")

        assertTrue(response.memories.isEmpty())
        assertEquals(emptyList<String>(), local.queries)
        assertEquals(emptyList<String>(), mem0.queries)
        assertEquals(0, local.sessionWrites)
        assertEquals(0, mem0.sessionWrites)
        assertEquals(0, local.eventWrites)
        assertEquals(0, mem0.eventWrites)
    }

    @Test
    fun `write failure rethrows after reporting failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"invalid token"}"""))
        val session = Session(
            key = SessionKey("Gimi", "user-default", "session-1"),
            events = mutableListOf(
                Event(author = "user", content = Content(role = Role.USER, parts = listOf(Part(text = "Hello")))),
            ),
        )

        val thrown = try {
            service.addSessionToMemory(session)
            null
        } catch (error: IOException) {
            error
        }
        assertTrue(thrown is IOException)
        assertEquals(MemoryOperation.WRITE, status.lastFailure)
    }

    @Test
    fun `addEventsToMemory sends only the delta events`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"event_id":"e1","status":"PENDING"}"""))
        val events = listOf(
            Event(author = "user", content = Content(role = Role.USER, parts = listOf(Part(text = "Hello")))),
            Event(author = "Assistant", content = Content(role = Role.MODEL, parts = listOf(Part(text = "Hi")))),
        )

        service.addEventsToMemory("Gimi", "user-default", events)

        val request = server.takeRequest()
        assertEquals("/v3/memories/add/", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("user-default", body.getValue("user_id").jsonPrimitive.content)
        assertEquals("Gimi", body.getValue("app_id").jsonPrimitive.content)
        val messages = body.getValue("messages").jsonArray
        assertEquals(listOf("user", "assistant"), messages.map { it.jsonObject.getValue("role").jsonPrimitive.content })
        assertEquals(listOf("Hello", "Hi"), messages.map { it.jsonObject.getValue("content").jsonPrimitive.content })
    }

    @Test
    fun `search tolerates null results without reporting failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"results":null}"""))

        val response = service.searchMemory("Gimi", "user-default", "query")

        assertTrue(response.memories.isEmpty())
        assertEquals(null, status.lastFailure)
    }

    @Test
    fun `search preserves quoted string metadata`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"results":[{"id":"m1","memory":"Likes tea","metadata":{"user_id":"00123","active":"false"},"created_at":"2026-08-29T00:00:00Z"}]}""".trimIndent(),
            ),
        )

        val response = service.searchMemory("Gimi", "user-default", "favorite drink")

        val metadata = response.memories.single().customMetadata
        assertEquals("00123", metadata["user_id"])
        assertEquals("false", metadata["active"])
    }
}

private class FakeMemorySettingsRepository(initial: MemoryConfiguration) : MemorySettingsRepository {
    val mutableConfiguration = MutableStateFlow(initial)
    override val configuration: StateFlow<MemoryConfiguration> = mutableConfiguration

    override suspend fun save(memoryEnabled: Boolean, mem0Enabled: Boolean, apiKey: String?) = Unit
}

private class RecordingMemoryService : com.google.adk.kt.memory.MemoryService {
    val queries = mutableListOf<String>()
    var sessionWrites = 0
    var eventWrites = 0

    override suspend fun addSessionToMemory(session: Session) {
        sessionWrites++
    }

    override suspend fun addEventsToMemory(
        appName: String,
        userId: String,
        events: List<com.google.adk.kt.events.Event>,
        sessionId: String?,
        customMetadata: Map<String, Any?>?,
    ) {
        eventWrites++
    }

    override suspend fun searchMemory(
        appName: String,
        userId: String,
        query: String,
    ): com.google.adk.kt.memory.SearchMemoryResponse {
        queries += query
        return com.google.adk.kt.memory.SearchMemoryResponse(emptyList())
    }
}
