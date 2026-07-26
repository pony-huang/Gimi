package github.ponyhuang.asssistantai.data.speech.remote

import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MinimaxTtsGatewayTest {

    private lateinit var server: MockWebServer
    private lateinit var gateway: MinimaxTtsGateway

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val client = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        gateway = MinimaxTtsGateway(
            httpClient = client,
            endpoint = server.url("").toString().trimEnd('/'),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("").toString().trimEnd('/')
    private fun config() = SpeechSynthesisConfig(
        baseUrl = baseUrl(),
        apiKey = "test-key-here",
        modelId = "speech-2.8-hd",
        voiceId = "male-qn-qingse",
    )

    @Test
    fun synthesize_streamsAllHexAudioChunksInOrder() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "[" +
                        "{\"data\":{\"audio\":\"aabb\",\"status\":1}}," +
                        "{\"data\":{\"audio\":\"ccdd\",\"status\":2}}" +
                        "]",
                ),
        )

        val bytes = gateway.synthesize(config(), "hello").toList()

        assertEquals(2, bytes.size)
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), bytes[0])
        assertArrayEquals(byteArrayOf(0xCC.toByte(), 0xDD.toByte()), bytes[1])
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(baseUrl() + "/", recorded.requestUrl.toString())
        assertEquals("Bearer test-key-here", recorded.getHeader("Authorization"))
        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals("speech-2.8-hd", body.get("model").asString)
        assertEquals("hello", body.get("text").asString)
        assertTrue(body.get("stream").asBoolean)
        assertEquals("male-qn-qingse", body.getAsJsonObject("voice_setting").get("voice_id").asString)
        val audio = body.getAsJsonObject("audio_setting")
        assertEquals(24000, audio.get("sample_rate").asInt)
        assertEquals("pcm", audio.get("format").asString)
        assertEquals(1, audio.get("channel").asInt)
        assertEquals("auto", body.get("language_boost").asString)
        assertTrue(
            body.getAsJsonObject("stream_options").get("exclude_aggregated_audio").asBoolean,
        )
    }

    /**
     * Regression for SSE-style framing (the documented content-type is
     * `text/event-stream` so the server may emit `data: {...}` lines
     * instead of a JSON array).
     */
    @Test
    fun synthesize_alsoAcceptsSseDataLinesFraming() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"data\":{\"audio\":\"aabb\",\"status\":1}}\n" +
                        "data: {\"data\":{\"audio\":\"ccdd\",\"status\":2}}\n",
                ),
        )

        val bytes = gateway.synthesize(config(), "hello").toList()

        assertEquals(2, bytes.size)
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte()), bytes[0])
        assertArrayEquals(byteArrayOf(0xCC.toByte(), 0xDD.toByte()), bytes[1])
    }

    @Test
    fun synthesize_throwsErrorWhenNoAudioEmitted() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("[{\"data\":{\"status\":2}}]"),
        )

        val ex = assertThrows(IOException::class.java) {
            runBlocking { gateway.synthesize(config(), "hello").toList() }
        }
        assertTrue(
            "Expected empty-stream message, got: ${ex.message}",
            ex.message?.contains("语音合成未返回音频数据") == true,
        )
    }

    @Test
    fun synthesize_throwsLocalizedErrorOnBaseRespFailure() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "[" +
                        "{\"data\":{\"audio\":\"aabb\",\"status\":1}," +
                        "\"base_resp\":{\"status_code\":1004,\"status_msg\":\"bad key\"}}" +
                        "]",
                ),
        )

        val ex = assertThrows(IOException::class.java) {
            runBlocking { gateway.synthesize(config(), "hello").toList() }
        }
        assertNotNull(ex.message)
        assertTrue(
            "Expected auth failure message, got: ${ex.message}",
            ex.message?.contains("鉴权失败") == true,
        )
    }

    @Test
    fun synthesize_throwsErrorOnHttpFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server boom"))

        val ex = assertThrows(IOException::class.java) {
            runBlocking { gateway.synthesize(config(), "hello").toList() }
        }
        assertNotNull(ex.message)
        assertTrue(
            "Expected HTTP 500 message, got: ${ex.message}",
            ex.message?.contains("HTTP 500") == true,
        )
        assertTrue(
            "Expected body snippet, got: ${ex.message}",
            ex.message?.contains("server boom") == true,
        )
    }

    /**
     * Locks in the hardcoded production endpoint. When no override is passed,
     * the gateway must target exactly `https://api.minimaxi.com/v1/t2a_v2` —
     * not anything derived from `config.baseUrl` or `apiBaseUrl`, which would
     * re-introduce the `/v1/v1/t2a_v2` 404.
     */
    @Test
    fun defaultEndpoint_targetsMinimaxT2aV2Url() = runTest {
        var capturedUrl: String? = null
        val interceptor = Interceptor { chain ->
            capturedUrl = chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    "[{\"data\":{\"audio\":\"aabb\",\"status\":2}}]".toResponseBody(),
                )
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val defaultGateway = MinimaxTtsGateway(client)

        defaultGateway.synthesize(config(), "hi").toList()

        assertEquals("https://api.minimaxi.com/v1/t2a_v2", capturedUrl)
    }
}
