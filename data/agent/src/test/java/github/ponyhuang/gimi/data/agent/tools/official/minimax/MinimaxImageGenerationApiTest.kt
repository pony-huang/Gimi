package github.ponyhuang.gimi.data.agent.tools.official.minimax

import kotlinx.coroutines.test.runTest
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

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("Bearer test-key", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"prompt\":\"一只橘猫\""))
            assertTrue(body.contains("\"aspect_ratio\":\"16:9\""))
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

            api.generate(
                prompt = "让人物穿宇航服",
                model = "image-01",
                aspectRatio = null,
                subjectReference = "https://image.example/source.png",
            )

            val request = server.takeRequest()
            assertTrue(request.body.readUtf8().contains("\"subject_reference\":[{\"type\":\"character\",\"image_file\":\"https://image.example/source.png\"}]"))
        }
    }
}
