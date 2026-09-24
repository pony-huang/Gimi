package github.ponyhuang.gimi.data.speech.remote

import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

class SpeechRecognitionGatewayFactoryTest {
    @Test
    fun create_routesMinimaxServiceToOfficialGateway() {
        val openAiCompatible = mockk<OpenAiCompatibleSpeechRecognitionGateway>()
        val minimax = mockk<MinimaxSpeechRecognitionGateway>()
        val factory = SpeechRecognitionGatewayFactory(openAiCompatible, minimax)

        assertSame(minimax, factory.create(config(serviceId = "minimax")))
        assertSame(openAiCompatible, factory.create(config(serviceId = "mimo")))
    }

    private fun config(serviceId: String) = SpeechRecognitionConfig(
        serviceId = serviceId,
        baseUrl = "https://example.test/v1",
        apiKey = "key",
        modelId = "model",
    )
}
