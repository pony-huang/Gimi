package github.ponyhuang.gimi.data.speech.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import github.ponyhuang.gimi.domain.speech.model.MinimaxTtsVoices
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceContext
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MinimaxVoiceProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: MinimaxVoiceProvider

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0

        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        provider = MinimaxVoiceProvider(
            httpClient = client,
            gson = Gson(),
            defaultEndpoint = server.url("/get_voice").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkStatic(Log::class)
    }

    @Test
    fun parseRemoteVoicesSuccessfully() = runTest {
        val jsonResponse = """
            {
              "base_resp": {
                "status_code": 0,
                "status_msg": "success"
              },
              "system_voice": [
                {
                  "voice_id": "male-qn-qingse",
                  "voice_name": "青涩男声",
                  "description": ["青涩", "音色清澈"]
                }
              ],
              "voice_cloning": [
                {
                  "voice_id": "clone_voice_001",
                  "description": ["我的专属声音"],
                  "created_time": "2024-05-20"
                }
              ],
              "voice_generation": [
                {
                  "voice_id": "gen_voice_002",
                  "description": ["AI生成音色"],
                  "created_time": "2024-06-01"
                }
              ]
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse),
        )

        val context = TtsVoiceContext(
            serviceId = "minimax",
            apiKey = "test_api_key",
            baseUrl = server.url("").toString().trimEnd('/'),
        )

        val voices = provider.getVoices(context)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("Bearer test_api_key", recorded.getHeader("Authorization"))
        val requestBody = Gson().fromJson(recorded.body.readUtf8(), JsonObject::class.java)
        assertEquals("all", requestBody.get("voice_type").asString)

        assertEquals(3, voices.size)

        val systemVoice = voices[0]
        assertEquals("male-qn-qingse", systemVoice.id)
        assertEquals("青涩男声", systemVoice.name)
        assertEquals("青涩 / 音色清澈", systemVoice.description)

        val cloneVoice = voices[1]
        assertEquals("clone_voice_001", cloneVoice.id)
        assertEquals("我的专属声音", cloneVoice.name)
        assertTrue(cloneVoice.description?.contains("克隆音色") == true)
        assertTrue(cloneVoice.description?.contains("2024-05-20") == true)

        val genVoice = voices[2]
        assertEquals("gen_voice_002", genVoice.id)
        assertEquals("AI生成音色", genVoice.name)
        assertTrue(genVoice.description?.contains("生成音色") == true)
        assertTrue(genVoice.description?.contains("2024-06-01") == true)
    }

    @Test
    fun fallbackToLocalVoicesWhenApiKeyIsBlank() = runTest {
        val context = TtsVoiceContext(
            serviceId = "minimax",
            apiKey = "",
            baseUrl = server.url("").toString(),
        )

        val voices = provider.getVoices(context)
        assertEquals(MinimaxTtsVoices.all.size, voices.size)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun fallbackToLocalVoicesOnHttpError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        val context = TtsVoiceContext(
            serviceId = "minimax",
            apiKey = "test_key",
            baseUrl = server.url("").toString(),
        )

        val voices = provider.getVoices(context)
        assertEquals(MinimaxTtsVoices.all.size, voices.size)
        assertEquals(MinimaxTtsVoices.all.map { it.id }, voices.map { it.id })
    }

    @Test
    fun fallbackToLocalVoicesOnApiStatusCodeFailure() = runTest {
        val jsonResponse = """
            {
              "base_resp": {
                "status_code": 1004,
                "status_msg": "auth failed"
              }
            }
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(jsonResponse),
        )

        val context = TtsVoiceContext(
            serviceId = "minimax",
            apiKey = "invalid_key",
            baseUrl = server.url("").toString(),
        )

        val voices = provider.getVoices(context)
        assertEquals(MinimaxTtsVoices.all.size, voices.size)
    }
}
