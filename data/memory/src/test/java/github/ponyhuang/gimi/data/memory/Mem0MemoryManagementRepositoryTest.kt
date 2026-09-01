package github.ponyhuang.gimi.data.memory

import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryFeedback
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.junit.Before
import org.junit.Test

class Mem0MemoryManagementRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: Mem0MemoryManagementRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        repository = Mem0MemoryManagementRepositoryImpl(
            api = Mem0ApiClient(
                httpClient = OkHttpClient(),
                settingsRepository = TestMemorySettingsRepository(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `load page scopes Mem0 memories to the app user`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"count":1,"next":null,"previous":null,"results":[{"id":"m1","memory":"Likes tea","created_at":"2026-09-01T00:00:00Z","updated_at":"2026-09-01T01:00:00Z"}]}""",
            ),
        )

        val page = repository.loadPage(page = 1, pageSize = 50)

        val request = server.takeRequest()
        assertEquals("/v3/memories/?page=1&page_size=50", request.path)
        assertEquals("Token mem0-key", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Gimi", body.getValue("filters").jsonObject.getValue("AND").jsonArray[1].jsonObject.getValue("app_id").jsonPrimitive.content)
        assertEquals("m1", page.memories.single().id)
        assertEquals(Instant.parse("2026-09-01T01:00:00Z"), page.memories.single().updatedAt)
        assertEquals(false, page.hasNextPage)
    }

    @Test
    fun `delete removes one cloud memory`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        repository.delete("m1")

        val request = server.takeRequest()
        assertEquals("/v1/memories/m1/", request.path)
        assertEquals("DELETE", request.method)
    }

    @Test
    fun `negative feedback includes an optional reason`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        repository.submitFeedback("m1", ManagedMemoryFeedback.NEGATIVE, "No longer true")

        val request = server.takeRequest()
        assertEquals("/v1/feedback/", request.path)
        assertEquals("POST", request.method)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("m1", body.getValue("memory_id").jsonPrimitive.content)
        assertEquals("NEGATIVE", body.getValue("feedback").jsonPrimitive.content)
        assertEquals("No longer true", body.getValue("feedback_reason").jsonPrimitive.content)
    }
}

private class TestMemorySettingsRepository : MemorySettingsRepository {
    override val configuration: StateFlow<MemoryConfiguration> =
        MutableStateFlow(MemoryConfiguration(mem0Enabled = true, apiKey = "mem0-key"))

    override suspend fun save(memoryEnabled: Boolean, mem0Enabled: Boolean, apiKey: String?) = Unit
}
