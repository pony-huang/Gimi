package github.ponyhuang.asssistantai.data.speech.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechSynthesisGatewayFactoryTest {

    @Test
    fun createReturnsMinimaxGatewayWhenHostIsMinimax() = runTest {
        val minimax = mockk<MinimaxTtsGateway>(relaxed = true)
        val mimo = mockk<MiMoSpeechSynthesisGateway>(relaxed = true)
        val factory = SpeechSynthesisGatewayFactory(minimax, mimo)

        val config = SpeechSynthesisConfig(
            baseUrl = "https://api.minimaxi.com/v1",
            apiKey = "k",
            modelId = "speech-2.8-hd",
            voiceId = "v",
        )

        val gateway = factory.create(config)
        assertEquals(minimax, gateway)
        every { minimax.synthesize(config, "hi") } returns flowOf(byteArrayOf(0x01))
        gateway.synthesize(config, "hi").toList()

        verify(exactly = 1) { minimax.synthesize(config, "hi") }
        verify(exactly = 0) { mimo.synthesize(any(), any()) }
    }

    @Test
    fun createReturnsMiMoGatewayForUnrecognizedHost() = runTest {
        val minimax = mockk<MinimaxTtsGateway>(relaxed = true)
        val mimo = mockk<MiMoSpeechSynthesisGateway>(relaxed = true)
        val factory = SpeechSynthesisGatewayFactory(minimax, mimo)

        val config = SpeechSynthesisConfig(
            baseUrl = "https://api.xiaomimimo.com/v1",
            apiKey = "k",
            modelId = "mimo_default",
            voiceId = "v",
        )

        val gateway = factory.create(config)
        assertEquals(mimo, gateway)
        every { mimo.synthesize(config, "hi") } returns flowOf(byteArrayOf(0x02))
        gateway.synthesize(config, "hi").toList()

        verify(exactly = 0) { minimax.synthesize(any(), any()) }
        verify(exactly = 1) { mimo.synthesize(config, "hi") }
    }

    @Test
    fun createMatchesMinimaxHostCaseInsensitively() = runTest {
        val minimax = mockk<MinimaxTtsGateway>(relaxed = true)
        val mimo = mockk<MiMoSpeechSynthesisGateway>(relaxed = true)
        val factory = SpeechSynthesisGatewayFactory(minimax, mimo)

        val config = SpeechSynthesisConfig(
            baseUrl = "HTTPS://API.MINIMAXI.COM/v1",
            apiKey = "k",
            modelId = "x",
            voiceId = "y",
        )

        assertEquals(minimax, factory.create(config))
    }
}