package github.ponyhuang.gimi.plugin.spotify

import github.ponyhuang.gimi.pluginapi.PluginJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun spotifyForbiddenMessageGuidesAgentToUserOwnedContent() {
        val body = """{"error":{"status":403,"message":"Forbidden."}}"""

        val message = spotifyErrorMessage(403, body)

        assertTrue(message.contains("spotify_get_my_playlists"))
        assertTrue(message.contains("spotify_get_top_tracks"))
    }

    @Test
    fun spotifyNotFoundMessageAvoidsRetryingRemovedOrInaccessibleContent() {
        val body = """{"error":{"status":404,"message":"Resource not found"}}"""

        val message = spotifyErrorMessage(404, body)

        assertTrue(message.contains("Do not retry"))
        assertTrue(message.contains("Spotify Web API"))
    }

    @Test
    fun toJsonNativeRecursivelyConverts() {
        val json = JSONObject()
            .put("a", "x")
            .put("b", 1)
            .put("c", JSONArray().put("y").put(JSONObject().put("d", true)))
            .put("e", JSONObject.NULL)

        val native = PluginJson.toNative(json) as Map<String, Any?>

        assertEquals("x", native["a"])
        assertEquals(1, native["b"])
        assertEquals(listOf("y", mapOf("d" to true)), native["c"])
        assertNull(native["e"])
    }
}
