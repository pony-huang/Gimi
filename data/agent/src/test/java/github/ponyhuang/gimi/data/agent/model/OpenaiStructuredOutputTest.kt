package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenaiStructuredOutputTest {
    @Test
    fun mapsApplicationJsonToOpenAiJsonObjectResponseFormat() {
        val params = ExposedOpenai().createParams(
            LlmRequest(
                contents = listOf(
                    Content(role = Role.USER, parts = listOf(Part(text = "recommend"))),
                ),
                config = GenerateContentConfig(responseMimeType = "application/json"),
            ),
        )

        val responseFormat = params.responseFormat().orElseThrow()
        assertTrue(responseFormat.isJsonObject())
        assertEquals(
            "json_object",
            responseFormat.asJsonObject()._type().convert(String::class.java),
        )
    }

    private class ExposedOpenai : Openai(
        name = "test-model",
        client = mockk<OpenAIClient>(),
    ) {
        fun createParams(request: LlmRequest): ChatCompletionCreateParams = buildCreateParams(request)
    }
}
