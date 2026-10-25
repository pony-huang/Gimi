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

@Preview(name = "音乐播放器 · Spotify 浅色", showBackground = true, widthDp = 390, heightDp = 430)
@Composable
private fun MusicPlayerSpotifyPreview() {
    MusicPlayerPreviewCanvas(darkTheme = false) {
        MusicPlayerCard(
            data = musicPlayerPreviewData(provider = "Spotify"),
            onTogglePlayback = {},
            onNext = {},
            onOpenPlayer = {},
        )
    }
}

@Preview(
    name = "音乐播放器 · 网易云音乐 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 430,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MusicPlayerNetEasePreview() {
    MusicPlayerPreviewCanvas(darkTheme = true) {
        MusicPlayerCard(
            data = musicPlayerPreviewData(
                provider = "网易云音乐",
                trackUri = "netease:track:preview",
                trackUrl = "https://music.163.com/song?id=preview",
                playing = false,
            ),
            onTogglePlayback = {},
            onNext = {},
            onOpenPlayer = {},
        )
    }
}

@Preview(
    name = "音乐播放器 · QQ音乐 窄屏",
    showBackground = true,
    widthDp = 280,
    heightDp = 520,
    fontScale = 1.3f,
)
@Composable
private fun MusicPlayerNarrowPreview() {
    MusicPlayerPreviewCanvas(darkTheme = false) {
        MusicPlayerCard(
            data = musicPlayerPreviewData(
                provider = "QQ音乐",
                title = "A Very Long Track Title That Needs Ellipsis",
                artist = "A Very Long Artist Name",
                album = "Collected Works",
                trackUri = "qqmusic:track:preview",
                trackUrl = "https://y.qq.com/n/ryqq/songDetail/preview",
            ),
            onTogglePlayback = {},
            onNext = {},
            onOpenPlayer = {},
        )
    }
}

@Preview(name = "音乐播放器 · Apple Music 无设备", showBackground = true, widthDp = 390, heightDp = 430)
@Composable
private fun MusicPlayerApplePreview() {
    MusicPlayerPreviewCanvas(darkTheme = false) {
        MusicPlayerCard(
            data = musicPlayerPreviewData(
                provider = "Apple Music",
                trackUri = "applemusic:track:preview",
                trackUrl = "https://music.apple.com/us/song/preview",
                deviceName = null,
            ),
            onTogglePlayback = {},
            onNext = {},
            onOpenPlayer = {},
        )
    }
}

@Composable
private fun MusicPlayerPreviewCanvas(darkTheme: Boolean, content: @Composable () -> Unit) {
    AsssistantaiTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

private fun musicPlayerPreviewData(
    provider: String = "Spotify",
    title: String = "夜航",
    artist: String = "陈婧霏",
    album: String = "发生关系",
    playing: Boolean = true,
    progressMs: Long = 72_000L,
    trackUri: String? = "spotify:track:preview",
    trackUrl: String? = "https://open.spotify.com/track/preview",
    deviceName: String? = "客厅音响",
): MusicPlayerData = MusicPlayerData(
    provider = provider,
    providerIconUrl = null,
    trackUri = trackUri,
    title = title,
    artist = artist,
    album = album,
    artworkUrl = null,
    durationMs = 258_000L,
    progressMs = progressMs,
    isPlaying = playing,
    fetchedAtEpochMs = System.currentTimeMillis() - progressMs,
    deviceId = "preview-device",
    deviceName = deviceName,
    trackUrl = trackUrl,
    isSaved = true,
    shuffleEnabled = false,
    repeatMode = "context",
    canSeek = true,
    canPrevious = true,
    canShuffle = true,
    canRepeat = true,
    canSave = true,
    playbackContext = MusicPlaybackContext.NOW_PLAYING,
)
