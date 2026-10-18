package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.material3.MiniController
import androidx.media3.ui.compose.material3.Player as Media3Player
import github.ponyhuang.gimi.feature.chat.R
import kotlinx.coroutines.delay

/** 将 A2UI 视频接入 Media3 官方播放器，播放控件由官方组件提供。 */
internal object ChatA2uiVideoRenderer : androidx.compose.material3.a2ui.catalog.A2uiVideoRenderer {
    @Composable
    override fun Video(
        url: String,
        modifier: Modifier,
        onError: (throwable: Throwable?) -> Unit,
    ) {
        val player = rememberA2uiPlayer(url, C.AUDIO_CONTENT_TYPE_MOVIE, onError)
        var controlsVisible by remember(player) { mutableStateOf(true) }
        var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
        LaunchedEffect(player, isPlaying, controlsVisible) {
            if (!isPlaying) {
                controlsVisible = true
            } else if (controlsVisible) {
                // 只在播放时自动隐藏，暂停、结束或缓冲时保留继续播放的入口。
                delay(3_000)
                controlsVisible = false
            }
        }
        Media3Player(
            player = player,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(player) {
                    detectTapGestures { controlsVisible = !controlsVisible }
                },
            // 保留聊天滚动容器内的 TextureView，按钮及进度条继续使用官方实现。
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            showControls = controlsVisible,
        )
    }
}

/** 将 A2UI 音频接入 Media3 官方紧凑控制器，以媒体标题承载音频说明。 */
internal object ChatA2uiAudioPlayerRenderer : androidx.compose.material3.a2ui.catalog.A2uiAudioPlayerRenderer {
    @Composable
    override fun AudioPlayer(
        url: String,
        contentDescription: String?,
        modifier: Modifier,
        onError: (throwable: Throwable?) -> Unit,
    ) {
        val title = contentDescription?.takeIf(String::isNotBlank) ?: stringResource(R.string.chat_a2ui_audio)
        val player = rememberA2uiPlayer(url, C.AUDIO_CONTENT_TYPE_MUSIC, onError, title)
        MiniController(player = player, modifier = modifier.fillMaxWidth())
    }
}

@Composable
private fun rememberA2uiPlayer(
    url: String,
    contentType: Int,
    onError: (Throwable?) -> Unit,
    title: String? = null,
): ExoPlayer {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val currentOnError by rememberUpdatedState(onError)
    val player = remember(url, contentType, context, isPreview) {
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
    LaunchedEffect(player, url, title, isPreview) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()
        if (player.mediaItemCount == 0) {
            player.setMediaItem(mediaItem)
            // 预览仅提供元数据和空闲状态，避免布局渲染触发网络请求。
            if (!isPreview) player.prepare()
        } else {
            // 说明文字更新不应重建播放器或重置播放进度。
            player.replaceMediaItem(0, mediaItem)
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
