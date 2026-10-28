package github.ponyhuang.gimi.plugin.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyApiTest {

    @Test
    fun spotifyErrorMessageExtractsNestedMessage() {
        val body = """{"error":{"status":403,"message":"The request is malformed.","reason":"PLAYER_COMMAND_REQUIRED"}}"""

        assertEquals("The request is malformed.", spotifyErrorMessage(403, body))
    }

    @Test
    fun spotifyErrorMessageFallsBackToStatus() {
        val message = spotifyErrorMessage(429, "not json")

        assertTrue(message.contains("429"))
    }

    @Test
    fun spotifyRateLimitMessageSurfacesRetryExhaustion() {
        val body = """{"error":{"status":429,"message":"Rate limit exceeded"}}"""

        val message = spotifyErrorMessage(429, body)

        assertTrue(message.contains("429"))
        assertTrue(message.contains("rate limit"))
    }

}
