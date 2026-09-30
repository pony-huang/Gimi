package github.ponyhuang.gimi.data.agent.tools.official.minimax

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MiniMax image API 的请求映射与返回图片链接解析。 */
class MinimaxImageGenerationApiTest {

    @Test
    fun generatePostsTextToImageRequestAndReturnsUrls() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"data":{"image_urls":["https://image.example/one.png"]},"base_resp":{"status_code":0,"status_msg":"success"}}""",
                ),
            )
            val api = MinimaxImageGenerationApi(
                apiKey = "test-key",
                httpClient = OkHttpClient(),
                endpoint = server.url("v1/image_generation").toString(),
            )

            val result = api.generate(
                prompt = "一只橘猫",
                model = "image-01",
                aspectRatio = "16:9",
                subjectReference = null,
            )

            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("POST", request.method)
            assertEquals("/v1/image_generation", request.path)
            assertEquals("Bearer test-key", request.getHeader("Authorization"))
            assertEquals(
                Json.parseToJsonElement("""{"model":"image-01","prompt":"一只橘猫","response_format":"url","aspect_ratio":"16:9"}"""),
                Json.parseToJsonElement(request.body.readUtf8()),
            )
            assertEquals(listOf("https://image.example/one.png"), result)
        }
    }

    @Test
    fun generateIncludesCharacterReferenceForImageToImage() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"data":{"image_urls":["https://image.example/result.png"]},"base_resp":{"status_code":0}}""",
                ),
            )
            val api = MinimaxImageGenerationApi(
                apiKey = "test-key",
                httpClient = OkHttpClient(),
                endpoint = server.url("v1/image_generation").toString(),
            )

            val result = api.generate(
                prompt = "让人物穿宇航服",
                model = "image-01",
                aspectRatio = null,
                subjectReference = "https://image.example/source.png",
            )

            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals(
                Json.parseToJsonElement("""{"model":"image-01","prompt":"让人物穿宇航服","response_format":"url","subject_reference":[{"type":"character","image_file":"https://image.example/source.png"}]}"""),
                Json.parseToJsonElement(request.body.readUtf8()),
            )
            assertEquals(listOf("https://image.example/result.png"), result)
        }
    }

    @Test
    fun httpFailureIsRejectedEvenIfBodyContainsImageUrls() = runTest {
        val error = failureFor(
            body = """{"data":{"image_urls":["https://image.example/unusable.png"]},"base_resp":{"status_code":0}}""",
            httpCode = 503,
        )
        assertTrue(error.message.orEmpty().contains("HTTP 503"))
    }

    @Test
    fun providerFailureIsRejectedEvenIfBodyContainsImageUrls() = runTest {
        val error = failureFor(
            body = """{"data":{"image_urls":["https://image.example/unusable.png"]},"base_resp":{"status_code":1004,"status_msg":"quota denied"}}""",
        )
        assertTrue(error.message.orEmpty().contains("quota denied"))
    }

    @Test
    fun successfulStatusWithoutImagesIsRejected() = runTest {
        val error = failureFor(body = """{"data":{"image_urls":[]},"base_resp":{"status_code":0}}""")
        assertTrue(error.message.orEmpty().contains("no image URLs"))
    }

    private suspend fun failureFor(body: String, httpCode: Int = 200): IllegalStateException {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(httpCode).setBody(body))
            val api = MinimaxImageGenerationApi(
                apiKey = "test-key",
                httpClient = OkHttpClient(),
                endpoint = server.url("v1/image_generation").toString(),
            )
            val error = runCatching {
                api.generate("prompt", "image-01", aspectRatio = null, subjectReference = null)
            }.exceptionOrNull()
            assertTrue("错误响应不能当成成功图片", error is IllegalStateException)
            return error as IllegalStateException
        }
    }
}
