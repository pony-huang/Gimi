package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyNowPlayingSnapshotTest {
    @Test
    fun nowPlayingAddsFetchedTimestampForUiProgressExtrapolation() = runTest {
        val api = mockk<SpotifyApi>()
        coEvery {
            api.get("/me/player", any())
        } returns JSONObject()
            .put("progress_ms", 72_000)
            .put("is_playing", true)
            .put("shuffle_state", false)
            .put("repeat_state", "track")
            .put("device", JSONObject().put("id", "speaker").put("is_restricted", true))
            .put("actions", JSONObject().put("disallows", JSONObject().put("seeking", true)))
        val tool = libraryTools(api).single { it.name == "spotify_now_playing" }

        val before = System.currentTimeMillis()
        val result = (tool as SpotifyTool).execute(
            mockk<ToolContext>(relaxed = true),
            emptyMap<String, Any?>(),
        ) as Map<*, *>
        val after = System.currentTimeMillis()
        val snapshot = result["result"] as Map<*, *>
        val fetchedAt = snapshot["fetched_at_ms"] as Long

        assertTrue(fetchedAt in before..after)
        assertEquals(false, snapshot["shuffle_state"])
        assertEquals("track", snapshot["repeat_state"])
        assertEquals(true, (snapshot["device"] as Map<*, *>)["is_restricted"])
        assertEquals(true, ((snapshot["actions"] as Map<*, *>)["disallows"] as Map<*, *>)["seeking"])
    }
}
