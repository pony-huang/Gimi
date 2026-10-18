package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Preview(name = "Spotify 播放器 · 浅色", showBackground = true, widthDp = 390, heightDp = 170)
@Composable
private fun SpotifyPlayerLightPreview() {
    SpotifyPreviewCanvas(darkTheme = false) {
        SpotifyPlayerCard(
            data = spotifyPlayerPreviewData(),
            onTogglePlayback = {},
            onNext = {},
            onOpenSpotify = {},
        )
    }
}

@Preview(
    name = "Spotify 播放器 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 170,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SpotifyPlayerDarkPreview() {
    SpotifyPreviewCanvas(darkTheme = true) {
        SpotifyPlayerCard(
            data = spotifyPlayerPreviewData(),
            onTogglePlayback = {},
            onNext = {},
            onOpenSpotify = {},
        )
    }
}

@Preview(
    name = "Spotify 播放器 · 窄屏暂停态",
    showBackground = true,
    widthDp = 280,
    heightDp = 200,
    fontScale = 1.3f,
)
@Composable
private fun SpotifyPlayerNarrowPreview() {
    SpotifyPreviewCanvas(darkTheme = false) {
        SpotifyPlayerCard(
            data = spotifyPlayerPreviewData(
                title = "A Long Spotify Track Title",
                artist = "A Very Long Artist Name",
                album = "Collected Works",
                playing = false,
                progressMs = 0L,
            ),
            onTogglePlayback = {},
            onNext = {},
            onOpenSpotify = {},
        )
    }
}

@Composable
private fun SpotifyPreviewCanvas(darkTheme: Boolean, content: @Composable () -> Unit) {
    AsssistantaiTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

private fun spotifyPlayerPreviewData(
    title: String = "夜航",
    artist: String = "陈婧霏",
    album: String = "发生关系",
    playing: Boolean = true,
    progressMs: Long = 72_000L,
): SpotifyPlayerData = SpotifyPlayerData(
    trackUri = "spotify:track:preview",
    title = title,
    artist = artist,
    album = album,
    artworkUrl = null,
    durationMs = 258_000L,
    progressMs = progressMs,
    isPlaying = playing,
    fetchedAtEpochMs = System.currentTimeMillis() - progressMs,
    deviceId = "preview-device",
    deviceName = "客厅音响",
    trackUrl = "https://open.spotify.com/track/preview",
    playbackContext = SpotifyPlaybackContext.NOW_PLAYING,
)
