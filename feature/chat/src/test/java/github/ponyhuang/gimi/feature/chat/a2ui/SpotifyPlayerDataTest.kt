package github.ponyhuang.gimi.feature.chat.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPlayerDataTest {
    @Test
    fun playingSnapshotAdvancesFromFetchedTime() {
        val data = requireNotNull(
            SpotifyPlayerData.fromA2ui(
                mapOf(
                    "trackUri" to "spotify:track:one",
                    "title" to "Night Flight",
                    "artist" to "Artist",
                    "durationMs" to 258_000L,
                    "progressMs" to 72_000L,
                    "isPlaying" to true,
                    "fetchedAtEpochMs" to 1_000L,
                ),
            ),
        )

        assertEquals(73_000L, data.positionAt(2_000L))
        assertEquals(258_000L, data.positionAt(999_000L))
    }

    @Test
    fun pausedSnapshotStaysAtKnownPosition() {
        val data = requireNotNull(
            SpotifyPlayerData.fromA2ui(
                mapOf(
                    "trackUri" to "spotify:track:one",
                    "title" to "Night Flight",
                    "durationMs" to 258_000L,
                    "progressMs" to 42_000L,
                    "isPlaying" to false,
                    "fetchedAtEpochMs" to 1_000L,
                ),
            ),
        )

        assertEquals(42_000L, data.positionAt(999_000L))
    }

    @Test
    fun missingSnapshotTimestampKeepsStablePositionAcrossRebinds() {
        val payload = mapOf(
            "trackUri" to "spotify:track:one",
            "title" to "Night Flight",
            "durationMs" to 258_000L,
            "progressMs" to 42_000L,
            "isPlaying" to true,
        )

        val first = requireNotNull(SpotifyPlayerData.fromA2ui(payload))
        val second = requireNotNull(SpotifyPlayerData.fromA2ui(payload))

        assertEquals(0L, first.fetchedAtEpochMs)
        assertEquals(0L, second.fetchedAtEpochMs)
        assertEquals(42_000L, first.positionAt(999_000L))
        assertEquals(first.positionAt(999_000L), second.positionAt(999_000L))
    }

    @Test
    fun rejectsPayloadWithoutPlayableIdentityOrDuration() {
        assertNull(
            SpotifyPlayerData.fromA2ui(
                mapOf("title" to "Missing URI", "durationMs" to 1_000L),
            ),
        )
        assertNull(
            SpotifyPlayerData.fromA2ui(
                mapOf("trackUri" to "spotify:track:one", "title" to "Missing duration"),
            ),
        )
    }

    @Test
    fun actionPayloadDropsNullContextForMiniPlayerCommand() {
        val action = spotifyPlayerAction(
            name = "spotify_next",
            context = mapOf(
                "trackUri" to "spotify:track:one",
                "deviceId" to null,
            ),
        )
        val event = action.getValue("event") as Map<*, *>
        val context = event["context"] as Map<*, *>

        assertEquals("spotify_next", event["name"])
        assertEquals("spotify:track:one", context["trackUri"])
        assertTrue("deviceId" !in context)
    }
}
