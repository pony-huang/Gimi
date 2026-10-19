package github.ponyhuang.gimi.plugin.spotify

import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.plugin.spotify.tools.SpotifyTool
import github.ponyhuang.gimi.plugin.spotify.tools.libraryTools
import github.ponyhuang.gimi.plugin.spotify.tools.playbackTools
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

class SpotifyHttpBoundaryTest {
    private val connection = mockk<HttpURLConnection>(relaxed = true)
    private var requestedUrl = ""
    private val auth = mockk<SpotifyAuth> {
        every { requireAccessToken() } returns "test-token"
    }
    private val api = SpotifyApi(mockk(), auth) { url ->
        requestedUrl = url
        connection
    }

    private fun respond(body: String, status: Int = 200) {
        every { connection.responseCode } returns status
        every { connection.inputStream } answers { body.byteInputStream() }
    }

    @Test
    fun libraryMutationsSendUrisInQuery() = runTest {
        respond("")
        for (name in listOf("spotify_save_to_library", "spotify_remove_from_library")) {
            val tool = libraryTools(api).single { it.name == name } as SpotifyTool
            val result = tool.execute(mockk<ToolContext>(), mapOf("ids" to listOf("track-id"))) as Map<*, *>
            assertFalse(result.containsKey("error"))
            assertTrue(requestedUrl.contains("?uris=spotify%3Atrack%3Atrack-id"))
        }
    }

    @Test
    fun containsReturnsBooleanArray() = runTest {
        respond("[false,true]")
        val tool = libraryTools(api).single { it.name == "spotify_check_library" } as SpotifyTool
        val result = tool.execute(mockk<ToolContext>(), mapOf("ids" to listOf("a", "b"))) as Map<*, *>
        assertEquals(listOf(false, true), result["result"])
    }

    @Test
    fun playbackSettingsUsePut() = runTest {
        respond("")
        for ((name, args) in listOf(
            "spotify_seek" to mapOf("position_ms" to 100),
            "spotify_set_repeat" to mapOf("state" to "track"),
            "spotify_set_shuffle" to mapOf("state" to true),
        )) {
            val tool = playbackTools(api).single { it.name == name } as SpotifyTool
            tool.execute(mockk<ToolContext>(), args)
            io.mockk.verify { connection.requestMethod = "PUT" }
            io.mockk.clearMocks(connection, answers = false)
        }
    }

    @Test
    fun acceptedPlayerCommandDoesNotParseResponseAsObject() = runTest {
        respond("Accepted")
        api.post("/me/player/next")
    }

    @Test
    fun playReturnsPausedObservationInsteadOfClaimingPlaybackStarted() = runTest {
        every { connection.responseCode } returns 200
        every { connection.inputStream } answers {
            when {
                requestedUrl.endsWith("/devices") ->
                    """{"devices":[{"id":"device","is_active":true}]}"""
                requestedUrl.contains("/play?") -> ""
                else -> """{"is_playing":false,"progress_ms":0}"""
            }.byteInputStream()
        }
        val tool = playbackTools(api).single { it.name == "spotify_play" } as SpotifyTool
        val result = tool.execute(mockk<ToolContext>(), mapOf("uri" to "spotify:track:a")) as Map<*, *>
        val payload = result["result"] as Map<*, *>
        assertTrue(payload["message"].toString().contains("request accepted"))
        assertEquals(false, (payload["observed_playback"] as Map<*, *>)["is_playing"])
    }

    @Test
    fun disconnectedPlayerGetsDeviceRecoveryGuidance() = runTest {
        respond("", 404)
        every { connection.errorStream } answers {
            """{"error":{"message":"Not found."}}""".byteInputStream()
        }
        val tool = playbackTools(api).single { it.name == "spotify_next" } as SpotifyTool
        val result = tool.execute(mockk<ToolContext>(), emptyMap()) as Map<*, *>
        assertTrue(result["error"].toString().contains("spotify_get_devices"))
        assertFalse(result["error"].toString().contains("Do not retry"))
    }
}
