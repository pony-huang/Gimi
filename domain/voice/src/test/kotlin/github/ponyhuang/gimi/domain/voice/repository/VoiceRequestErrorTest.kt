package github.ponyhuang.gimi.domain.voice.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRequestErrorTest {
    @Test
    fun preservesTheStatusAndNewProviderCodeWhenTheMessageIsTruncated() {
        val error = requireNotNull(VoiceRequestError.fromProvider(
            code = "new_realtime_error",
            message = "The provider returned a long explanation. ".repeat(10),
            httpStatus = 503,
        ))

        assertEquals(503, error.httpStatus)
        assertEquals("new_realtime_error", error.code)
        assertTrue(requireNotNull(error.message).endsWith("…"))
    }

    @Test
    fun omitsMessagesContainingCredentialsOrEchoedInput() {
        for (message in listOf("Authorization: Bearer private-token", "Rejected input: ‘private transcript’")) {
            val error = requireNotNull(VoiceRequestError.fromProvider(code = "rejected", message = message))
            assertEquals("rejected", error.code)
            assertNull(error.message)
        }
        assertNull(VoiceRequestError.fromProvider(code = "sk-secret"))
    }

    @Test
    fun unwrapsTypedServiceDetailsAndIgnoresLocalExceptionMessages() {
        val serviceError = VoiceRequestError.fromProvider(httpStatus = 401)
        val service = VoiceAuthenticationException("raw response", requestError = serviceError)
        val backend = VoiceAudioBackendException(RealtimeVoiceFailure.Recognition, cause = service)

        assertEquals(serviceError, backend.voiceRequestError())
        assertNull(IllegalStateException("raw response").voiceRequestError())
    }
}
