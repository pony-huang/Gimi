package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.modelaccess.ModelConfig
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemiCascadedMiMoRoutingTest {
    @Test
    fun mimoConfiguredForMessagesSendsWavToChatCompletions() = runBlocking {
        assertWavRoutesToChatCompletions(ApiProtocol.Anthropic, voice = true)
    }

    @Test
    fun textAfterWavUsesTheAudioCapableEndpointForMessagesConfiguration() = runBlocking {
        assertWavRoutesToChatCompletions(ApiProtocol.Anthropic, voice = false)
    }

    @Test
    fun textAfterWavUsesTheAudioCapableAdapterForChatCompletionsConfiguration() = runBlocking {
        assertWavRoutesToChatCompletions(ApiProtocol.OpenaiChatcompletions, voice = false)
    }

    @Test
    fun textOnlyUsesTheConfiguredMessagesEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json").setBody(
                    """{"id":"msg-1","type":"message","role":"assistant","model":"mimo-v2.6-pro","content":[{"type":"text","text":"ok"}],"stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}""",
                ),
            )
            val model = DefaultAgentLLMModelFactory(mockk()).createModel(ModelConfig(
                serviceId = "my-mimo",
                templateId = "mimo",
                baseType = ApiProtocol.Anthropic,
                modelId = "mimo-v2.6-pro",
                apiKey = "test-key",
                fullBaseUrl = server.url("/anthropic").toString(),
            ))
            model.generateContent(
                LlmRequest(model = model, contents = listOf(Content.fromText(Role.USER, "Text only"))),
                stream = false,
            ).toList()

            val sent = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/anthropic/v1/messages", sent.path)
        }
    }

    private suspend fun assertWavRoutesToChatCompletions(protocol: ApiProtocol, voice: Boolean) {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"choices":[{"message":{"role":"assistant","content":"hello"}}]}"""),
            )
            val config = ModelConfig(
                serviceId = "my-mimo",
                templateId = "mimo",
                baseType = protocol,
                modelId = "mimo-v2.6-pro",
                apiKey = "test-key",
                fullBaseUrl = server.url(if (protocol == ApiProtocol.Anthropic) "/anthropic" else "/v1").toString(),
            )
            val request = LlmRequest(
                contents = listOf(
                    Content(
                        role = Role.USER,
                        parts = listOf(Part(inlineData = Blob(mimeType = "audio/wav", data = byteArrayOf(1, 2, 3)))),
                    ),
                ) + if (voice) emptyList() else listOf(
                    Content.fromText(Role.MODEL, "Voice reply"),
                    Content.fromText(Role.USER, "Text follow-up"),
                ),
            )
            val factory = DefaultAgentLLMModelFactory(mockk())
            val model = if (voice) factory.createSemiCascadedModel(config) else factory.createModel(config)
            model.generateContent(request.copy(model = model), stream = false)
                .toList()

            val sent = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/v1/chat/completions", sent.path)
            assertEquals("Bearer test-key", sent.getHeader("Authorization"))
            val body = sent.body.readUtf8()
            assertTrue(body.contains("\"type\":\"input_audio\""))
            assertTrue(body.contains("data:audio/wav;base64,AQID"))
            if (!voice) {
                assertTrue(body.contains("Voice reply"))
                assertTrue(body.contains("Text follow-up"))
            }
        }
    }
}
