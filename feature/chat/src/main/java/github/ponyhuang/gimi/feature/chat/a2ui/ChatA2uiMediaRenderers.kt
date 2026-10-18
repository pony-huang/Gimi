package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import github.ponyhuang.gimi.feature.chat.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/** A2UI Video 目录的聊天宿主实现；使用独立 ExoPlayer，并在离开组合时释放。 */
internal object ChatA2uiVideoRenderer : androidx.compose.material3.a2ui.catalog.A2uiVideoRenderer {
    @Composable
    override fun Video(
        url: String,
        modifier: Modifier,
        onError: (throwable: Throwable?) -> Unit,
    ) {
        val player = rememberA2uiPlayer(url, C.AUDIO_CONTENT_TYPE_MOVIE, onError)
        val playback = rememberPlaybackState(player)
        val playLabel = stringResource(R.string.chat_a2ui_play)
        val pauseLabel = stringResource(R.string.chat_a2ui_pause)
        val seekLabel = stringResource(R.string.chat_a2ui_seek)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
        ) {
            PlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            )
            if (playback.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
            } else if (!playback.isPlaying) {
                FilledIconButton(
                    onClick = playback::toggle,
                    modifier = Modifier.align(Alignment.Center),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = playLabel)
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.58f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = playback::toggle,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playback.isPlaying) pauseLabel else playLabel,
                    )
                }
                Text(
                    text = formatMediaTime(playback.positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
                MediaProgressSlider(
                    state = playback,
                    sliderLabel = seekLabel,
                    modifier = Modifier.weight(1f),
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.35f),
                )
                Text(
                    text = formatMediaTime(playback.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** A2UI AudioPlayer 目录的聊天宿主实现；与视频共用进度、音频焦点和释放规则。 */
internal object ChatA2uiAudioPlayerRenderer :
    androidx.compose.material3.a2ui.catalog.A2uiAudioPlayerRenderer {
    @Composable
    override fun AudioPlayer(
        url: String,
        contentDescription: String?,
        modifier: Modifier,
        onError: (throwable: Throwable?) -> Unit,
    ) {
        val player = rememberA2uiPlayer(url, C.AUDIO_CONTENT_TYPE_MUSIC, onError)
        val playback = rememberPlaybackState(player)
        val playLabel = stringResource(R.string.chat_a2ui_play)
        val pauseLabel = stringResource(R.string.chat_a2ui_pause)
        val seekLabel = stringResource(R.string.chat_a2ui_seek)

        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = playback::toggle) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playback.isPlaying) pauseLabel else playLabel,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = contentDescription?.takeIf(String::isNotBlank)
                                ?: stringResource(R.string.chat_a2ui_audio),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                        )
                        Text(
                            text = "${formatMediaTime(playback.positionMs)} / ${formatMediaTime(playback.durationMs)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    if (playback.isBuffering) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                MediaProgressSlider(
                    state = playback,
                    sliderLabel = seekLabel,
                    modifier = Modifier.fillMaxWidth(),
                    activeColor = MaterialTheme.colorScheme.primary,
                    inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rememberA2uiPlayer(
    url: String,
    contentType: Int,
    onError: (Throwable?) -> Unit,
): ExoPlayer {
    val context = LocalContext.current
    val currentOnError by rememberUpdatedState(onError)
    val player = remember(url, contentType) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(contentType)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            playWhenReady = false
            setHandleAudioBecomingNoisy(true)
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentOnError(error)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return player
}

@Composable
private fun rememberPlaybackState(player: ExoPlayer): MediaPlaybackState {
    val state = remember(player) { MediaPlaybackState(player) }
    DisposableEffect(player, state) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                state.refresh()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(state) {
        while (isActive) {
            state.refresh()
            delay(250)
        }
    }
    return state
}

/** Compose 可观察的媒体状态；只保存控件需要的播放位置、时长和缓冲标志。 */
private class MediaPlaybackState(private val player: ExoPlayer) {
    var isPlaying by mutableStateOf(player.isPlaying)
        private set
    var isBuffering by mutableStateOf(player.playbackState == Player.STATE_BUFFERING)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    fun refresh() {
        isPlaying = player.isPlaying
        isBuffering = player.playbackState == Player.STATE_BUFFERING
        positionMs = player.currentPosition.coerceAtLeast(0L)
        durationMs = player.duration.coerceAtLeast(0L)
    }

    fun toggle() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        refresh()
    }

    fun seekToFraction(fraction: Float) {
        if (durationMs <= 0L) return
        player.seekTo((fraction.coerceIn(0f, 1f) * durationMs).toLong())
        refresh()
    }
}

@Composable
private fun MediaProgressSlider(
    state: MediaPlaybackState,
    sliderLabel: String,
    modifier: Modifier,
    activeColor: Color,
    inactiveColor: Color,
) {
    Slider(
        value = if (state.durationMs > 0L) state.positionMs.toFloat() / state.durationMs else 0f,
        onValueChange = state::seekToFraction,
        modifier = modifier.semantics { contentDescription = sliderLabel },
        enabled = state.durationMs > 0L,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
        ),
    )
}

private fun formatMediaTime(milliseconds: Long): String {
    val safeSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
