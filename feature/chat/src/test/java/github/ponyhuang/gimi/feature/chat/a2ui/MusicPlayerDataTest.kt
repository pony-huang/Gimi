package github.ponyhuang.gimi.feature.chat.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlayerDataTest {
    @Test
    fun playingSnapshotAdvancesFromFetchedTime() {
        val data = requireNotNull(
            MusicPlayerData.fromA2ui(
                mapOf(
                    "provider" to "Spotify",
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
            MusicPlayerData.fromA2ui(
                mapOf(
                    "provider" to "网易云音乐",
                    "trackUri" to "netease:track:one",
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
            "provider" to "QQ音乐",
            "trackUri" to "qqmusic:track:one",
            "title" to "Night Flight",
            "durationMs" to 258_000L,
            "progressMs" to 42_000L,
            "isPlaying" to true,
        )

        val first = requireNotNull(MusicPlayerData.fromA2ui(payload))
        val second = requireNotNull(MusicPlayerData.fromA2ui(payload))

        assertEquals(0L, first.fetchedAtEpochMs)
        assertEquals(0L, second.fetchedAtEpochMs)
        assertEquals(42_000L, first.positionAt(999_000L))
        assertEquals(first.positionAt(999_000L), second.positionAt(999_000L))
    }

    @Test
    fun acceptsProviderWithoutPlaybackIdentityOrDuration() {
        val data = requireNotNull(
            MusicPlayerData.fromA2ui(
                mapOf(
                    "provider" to "Apple Music",
                    "title" to "Night Flight",
                    "artist" to "Artist",
                    "trackUrl" to "https://music.apple.com/us/song/one",
                ),
            ),
        )

        assertEquals("Apple Music", data.provider)
        assertNull(data.trackUri)
        assertNull(data.durationMs)
        assertNull(data.positionAt(999_000L))
    }

    @Test
    fun missingCapabilitiesAndStatesNeverEnableNewControls() {
        val data = requireNotNull(MusicPlayerData.fromA2ui(mapOf("title" to "Track")))
        assertEquals(false, data.canSeek)
        assertEquals(false, data.canPrevious)
        assertEquals(false, data.canSave)
        assertNull(data.isSaved)
        assertNull(data.shuffleEnabled)
        assertNull(data.repeatMode)
    }

    @Test
    fun preservesVerifiedStatesAndRejectsUnknownRepeatMode() {
        val data = requireNotNull(MusicPlayerData.fromA2ui(mapOf(
            "title" to "Track", "isSaved" to false, "shuffleEnabled" to true,
            "repeatMode" to "track", "canSeek" to true, "canSave" to true,
        )))
        assertEquals(false, data.isSaved)
        assertEquals(true, data.shuffleEnabled)
        assertEquals("track", data.repeatMode)
        assertTrue(data.canSeek)
        assertTrue(data.canSave)
        assertNull(requireNotNull(MusicPlayerData.fromA2ui(mapOf("title" to "Track", "repeatMode" to "invalid"))).repeatMode)
    }

    @Test
    fun rejectsPayloadWithoutTitle() {
        assertNull(MusicPlayerData.fromA2ui(mapOf("provider" to "Spotify")))
    }

    @Test
    fun actionPayloadDropsNullContextForPlayerCommand() {
        val action = musicPlayerAction(
            name = "player_next",
            context = mapOf(
                "provider" to "Spotify",
                "trackUri" to "spotify:track:one",
                "deviceId" to null,
            ),
        )
        val event = action.getValue("event") as Map<*, *>
        val context = event["context"] as Map<*, *>

        assertEquals("player_next", event["name"])
        assertEquals("Spotify", context["provider"])
        assertEquals("spotify:track:one", context["trackUri"])
        assertTrue("deviceId" !in context)
    }
}
