package github.ponyhuang.gimi.feature.chat.a2ui.components

import android.graphics.Bitmap
import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.DynamicA2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiBooleanSchema
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.feature.chat.a2ui.rememberA2uiPlayer
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

/**
 * 音乐播放卡片的单曲元数据与播放资源描述，同时兼容网易云音乐与 Spotify。
 *
 * @property title 歌曲名称
 * @property artist 歌手或创作者名称
 * @property album 所属专辑名称
 * @property coverUrl 封面图片 URL
 * @property audioUrl 平台返回的音频流 URL（网易云 `playUrl`；Spotify 远程播放或未取详情时为空）
 * @property lyric 可选的单句歌词摘要
 * @property durationSeconds 歌曲时长（秒，自动兼容毫秒入参）
 * @property songId 平台曲目 ID 或 URI（网易云 32 位十六进制加密 ID，或 Spotify 曲目 ID / `spotify:track:xxx`）
 * @property originalId 纯数字或外部曲目 ID（网易云 `orpheus://song/<id>` 数字 ID，或 Spotify Track ID）
 * @property externalUrl 显式外部跳转链接（如 `https://open.spotify.com/track/...` 或 `orpheus://...`）
 * @property liked 是否已加入红心收藏
 * @property isTrial 是否为试听片段
 * @property isVip 是否受 VIP / Premium 权限限制
 */
internal data class MusicTrackItem(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val coverUrl: String = "",
    val audioUrl: String = "",
    val lyric: String = "",
    val durationSeconds: Int = 0,
    val songId: String = "",
    val originalId: String = "",
    val externalUrl: String = "",
    val liked: Boolean = false,
    val isTrial: Boolean = false,
    val isVip: Boolean = false,
)

/**
 * 从专辑/歌曲封面图像提取并做暗色沉浸化归一的四阶环境配色。
 *
 * @property highlight 左上光源与封面高光色
 * @property mid 卡片中部与歌单弹窗顶部过渡色
 * @property deep 卡片右下与歌单弹窗底部深邃底色
 * @property accent 歌词、角标与当前播放曲目高亮强调色
 */
internal data class CoverAmbientPalette(
    val highlight: Color,
    val mid: Color,
    val deep: Color,
    val accent: Color,
)

/** 基础默认主题色：网易红（NetEase Red，`#C20C0C` / `#E60026` 沉浸暗化梯度）。 */
internal val NeteaseRedAmbientPalette = CoverAmbientPalette(
    highlight = Color(0xFF8C141B),
    mid = Color(0xFF420B10),
    deep = Color(0xFF1C0609),
    accent = Color(0xFFFFB3B8),
)

/**
 * 同时兼容网易云音乐与 Spotify 的 A2UI 原生音乐播放卡片组件定义。
 *
 * - **网易云音乐**：支持 `audioUrl` 本地 Media3 流式播放、`songId`（32 位加密 ID）工具交互、
 *   `originalId`（数字 ID）唤起 `orpheus://` 客户端。
 * - **Spotify**：支持 Spotify Connect 远程状态快照（`isPlaying`、`progressSeconds`、`deviceId`）、
 *   `spotify:track:<id>` 或 `externalUrl` 唤起 Spotify 客户端，以及播放/切歌/收藏事件回传。
 * - **视觉**：基础主题色为网易红（[NeteaseRedAmbientPalette]），当封面图加载成功后自动提取封面主色调并平滑过渡。
 */
internal object ChatMusicPlayerCard : A2uiComponent {
    override val name = "MusicPlayerCard"
    override val description =
        "Play or control a song or multi-track queue in-chat with album cover waveform, NetEase-red base theme " +
            "with dynamic cover color extraction, track queue popup, favorite toggle, and client jump " +
            "(NetEase orpheus:// or Spotify spotify:/open.spotify.com). " +
            "For NetEase, populate audioUrl from netease_song_detail playUrl, songId (32-char hex), and originalId (numeric). " +
            "For Spotify, populate songId with track ID or spotify:track:URI, externalUrl, isPlaying, progressSeconds, and deviceId."

    private val titleProp = A2uiProperty.dynamicString(key = "title", required = true)
    private val artistProp = A2uiProperty.dynamicString(key = "artist")
    private val albumProp = A2uiProperty.dynamicString(key = "album")
    private val coverUrlProp = A2uiProperty.dynamicString(key = "coverUrl")
    private val audioUrlProp = A2uiProperty.dynamicString(key = "audioUrl")
    private val lyricProp = A2uiProperty.dynamicString(key = "lyric")
    private val durationProp = A2uiProperty.dynamicNumber(key = "durationSeconds")
    private val progressProp = A2uiProperty.dynamicNumber(key = "progressSeconds")
    private val isPlayingProp = A2uiProperty.dynamicBoolean(key = "isPlaying")
    private val deviceIdProp = A2uiProperty.dynamicString(key = "deviceId")
    private val providerProp = A2uiProperty.dynamicString(key = "provider")
    private val externalUrlProp = A2uiProperty.dynamicString(key = "externalUrl")
    private val songIdProp = A2uiProperty.dynamicString(key = "songId")
    private val originalIdProp = A2uiProperty.dynamicString(key = "originalId")
    private val playlistIdProp = A2uiProperty.dynamicString(key = "originalPlaylistId")
    private val playlistTitleProp = A2uiProperty.dynamicString(key = "playlistTitle")
    private val likedProp = A2uiProperty.dynamicBoolean(key = "liked")
    private val isTrialProp = A2uiProperty.dynamicBoolean(key = "isTrial")
    private val isVipProp = A2uiProperty.dynamicBoolean(key = "isVip")

    private val trackRecordSchema = A2uiObjectSchema(
        properties = mapOf(
            "title" to A2uiStringSchema.INSTANCE,
            "artist" to A2uiStringSchema.INSTANCE,
            "album" to A2uiStringSchema.INSTANCE,
            "coverUrl" to A2uiStringSchema.INSTANCE,
            "audioUrl" to A2uiStringSchema.INSTANCE,
            "lyric" to A2uiStringSchema.INSTANCE,
            "durationSeconds" to A2uiNumberSchema.INSTANCE,
            "songId" to A2uiStringSchema.INSTANCE,
            "originalId" to A2uiStringSchema.INSTANCE,
            "externalUrl" to A2uiStringSchema.INSTANCE,
            "liked" to A2uiBooleanSchema.INSTANCE,
            "isTrial" to A2uiBooleanSchema.INSTANCE,
            "isVip" to A2uiBooleanSchema.INSTANCE,
        ),
        required = setOf("title"),
        isAdditionalPropertiesAllowed = false,
    )

    private val tracksProp = A2uiProperty.dynamicCustom(
        key = "tracks",
        schema = A2uiAnySchema(
            keywords = listOf(
                A2uiSchemaKeyword.OneOf(
                    listOf(
                        A2uiArraySchema(items = trackRecordSchema, minItems = 0, maxItems = 50),
                        A2uiDataBindingSchema.DEFAULT_INSTANCE,
                        A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                    ),
                ),
            ),
        ),
        safeCast = { (it as? List<*>)?.takeIf { list -> list.size <= 50 } },
        required = false,
    )

    override val properties: List<A2uiProperty<*>> = listOf(
        titleProp,
        artistProp,
        albumProp,
        coverUrlProp,
        audioUrlProp,
        lyricProp,
        durationProp,
        progressProp,
        isPlayingProp,
        deviceIdProp,
        providerProp,
        externalUrlProp,
        songIdProp,
        originalIdProp,
        playlistIdProp,
        playlistTitleProp,
        likedProp,
        isTrialProp,
        isVipProp,
        tracksProp,
    )

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(titleProp) != null

    @Composable
    @Suppress("UNCHECKED_CAST")
    private fun A2uiComponentScope.readAny(
        properties: A2uiComponentProperties,
        property: DynamicA2uiProperty<*>,
    ): Any? = properties.bind(property as DynamicA2uiProperty<Any>)

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val values = properties.let { props ->
            this@ChatMusicPlayerCard.properties.associate { property ->
                property.key to when (property) {
                    is DynamicA2uiProperty<*> -> readAny(props, property)
                    else -> null
                }
            }
        }
        val likedUpdater = properties.bindUpdater(likedProp)
        MusicPlayerCard(
            values = CardValues(values),
            modifier = modifier,
            onUpdateLikedBinding = { likedUpdater?.invoke(it) },
            onOpenUrl = { url ->
                dispatchAction(
                    mapOf(
                        "functionCall" to mapOf(
                            "call" to "openUrl",
                            "args" to mapOf("url" to url),
                        ),
                    ),
                )
            },
            onSendEvent = { eventName, contextMap ->
                dispatchAction(
                    mapOf(
                        "event" to mapOf(
                            "name" to eventName,
                            "context" to contextMap,
                        ),
                    ),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicPlayerCard(
    values: CardValues,
    modifier: Modifier = Modifier,
    onUpdateLikedBinding: (Boolean) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onSendEvent: (String, Map<String, Any>) -> Unit = { _, _ -> },
) {
    val tracks = remember(values) { parseTracks(values) }
    var currentIndex by rememberSaveable(tracks.size) { mutableIntStateOf(0) }
    val safeIndex = currentIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
    val currentTrack = tracks.getOrNull(safeIndex) ?: return

    val topSongId = values.text("songId")
    val topTitle = values.text("title")
    LaunchedEffect(topSongId, topTitle, tracks) {
        val matched = tracks.indexOfFirst {
            (topSongId.isNotBlank() && it.songId == topSongId) ||
                (topSongId.isBlank() && topTitle.isNotBlank() && it.title == topTitle)
        }
        if (matched >= 0 && matched != currentIndex) {
            currentIndex = matched
        }
    }

    val likedOverrides = remember { mutableStateMapOf<Int, Boolean>() }
    val isLiked = likedOverrides[safeIndex] ?: currentTrack.liked

    var showQueueSheet by rememberSaveable { mutableStateOf(false) }

    // 基础默认主题色统一使用网易红，封面图加载成功后提取封面主色并平滑过渡
    var extractedPalette by remember(currentTrack.coverUrl, currentTrack.title, safeIndex) {
        mutableStateOf(NeteaseRedAmbientPalette)
    }
    val highlightColor by animateColorAsState(
        targetValue = extractedPalette.highlight,
        animationSpec = tween(420),
        label = "coverHighlight",
    )
    val midColor by animateColorAsState(
        targetValue = extractedPalette.mid,
        animationSpec = tween(420),
        label = "coverMid",
    )
    val deepColor by animateColorAsState(
        targetValue = extractedPalette.deep,
        animationSpec = tween(420),
        label = "coverDeep",
    )
    val accentColor by animateColorAsState(
        targetValue = extractedPalette.accent,
        animationSpec = tween(420),
        label = "coverAccent",
    )
    val animatedPalette = CoverAmbientPalette(highlightColor, midColor, deepColor, accentColor)

    val isPreview = LocalInspectionMode.current
    val hasAudioUrl = currentTrack.audioUrl.isNotBlank()
    val remoteIsPlaying = values.flag("isPlaying")
    val initialProgressSeconds = remember(values, safeIndex) {
        normalizeSeconds(values.number("progressSeconds"))
    }
    val deviceId = values.text("deviceId")
    val provider = values.text("provider")

    val player = if (hasAudioUrl) {
        rememberA2uiPlayer(
            url = currentTrack.audioUrl,
            contentType = C.AUDIO_CONTENT_TYPE_MUSIC,
            onError = {},
            title = currentTrack.title,
        )
    } else {
        null
    }

    var isPlaying by remember(player, isPreview, remoteIsPlaying, safeIndex) {
        mutableStateOf(
            when {
                hasAudioUrl -> isPreview
                else -> remoteIsPlaying
            },
        )
    }
    var positionMs by remember(player, safeIndex, initialProgressSeconds) {
        mutableLongStateOf(
            when {
                initialProgressSeconds > 0 -> initialProgressSeconds * 1000L
                isPreview && currentTrack.durationSeconds > 0 -> currentTrack.durationSeconds * 380L
                else -> 0L
            },
        )
    }
    var playerDurationMs by remember(player, safeIndex, currentTrack.durationSeconds) {
        mutableLongStateOf(currentTrack.durationSeconds * 1000L)
    }

    if (player != null) {
        DisposableEffect(player, safeIndex, tracks.size) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && player.duration > 0) {
                        playerDurationMs = player.duration
                    } else if (playbackState == Player.STATE_ENDED) {
                        if (safeIndex + 1 < tracks.size && tracks[safeIndex + 1].audioUrl.isNotBlank()) {
                            currentIndex = safeIndex + 1
                        } else {
                            isPlaying = false
                            player.seekTo(0)
                            player.pause()
                        }
                    }
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }

        LaunchedEffect(player, isPlaying) {
            while (isPlaying) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                if (player.duration > 0) playerDurationMs = player.duration
                delay(250)
            }
        }
    } else {
        // 远程遥控模式（如 Spotify Connect）：当处于播放态且非 Preview 时，按秒平滑推算本地进度条
        LaunchedEffect(isPlaying, isPreview, safeIndex, playerDurationMs) {
            if (!isPreview && isPlaying && playerDurationMs > 0L) {
                while (isPlaying && positionMs < playerDurationMs) {
                    delay(1_000)
                    positionMs = (positionMs + 1_000L).coerceAtMost(playerDurationMs)
                }
            }
        }
    }

    val totalDurationMs = when {
        playerDurationMs > 0L -> playerDurationMs
        currentTrack.durationSeconds > 0 -> currentTrack.durationSeconds * 1000L
        else -> 0L
    }
    val clampedPositionMs = if (totalDurationMs > 0L) {
        positionMs.coerceIn(0L, totalDurationMs)
    } else {
        positionMs.coerceAtLeast(0L)
    }
    val progressFraction = if (totalDurationMs > 0L) {
        (clampedPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val originalPlaylistId = values.text("originalPlaylistId")
    val playlistTitle = values.text("playlistTitle").ifBlank {
        stringResource(R.string.chat_a2ui_music_queue_title)
    }

    fun isSpotifyTrack(track: MusicTrackItem): Boolean =
        provider.equals("spotify", ignoreCase = true) ||
            track.songId.startsWith("spotify:", ignoreCase = true) ||
            track.externalUrl.contains("spotify.com", ignoreCase = true) ||
            track.externalUrl.startsWith("spotify:", ignoreCase = true)

    fun buildTrackEventContext(track: MusicTrackItem, extra: Map<String, Any> = emptyMap()): Map<String, Any> = buildMap {
        if (track.songId.isNotBlank()) {
            put("songId", track.songId)
            val trackUri = if (track.songId.startsWith("spotify:")) {
                track.songId
            } else if (isSpotifyTrack(track)) {
                "spotify:track:${track.songId}"
            } else {
                ""
            }
            if (trackUri.isNotBlank()) put("trackUri", trackUri)
        }
        put("title", track.title)
        if (track.artist.isNotBlank()) put("artist", track.artist)
        if (track.originalId.isNotBlank()) put("originalId", track.originalId)
        if (deviceId.isNotBlank()) put("deviceId", deviceId)
        if (provider.isNotBlank()) put("provider", provider)
        putAll(extra)
    }

    fun selectTrackAt(targetIndex: Int) {
        val bounded = targetIndex.coerceIn(0, tracks.lastIndex)
        currentIndex = bounded
        val target = tracks[bounded]
        if (target.audioUrl.isBlank() && target.songId.isNotBlank()) {
            onSendEvent(
                "play_track",
                buildTrackEventContext(target, mapOf("playbackContext" to "track_result")),
            )
        }
    }

    fun handlePlayPause() {
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player.play()
            }
        } else if (currentTrack.songId.isNotBlank() || deviceId.isNotBlank() || isSpotifyTrack(currentTrack)) {
            val currentPlaying = isPlaying
            isPlaying = !currentPlaying
            val actionName = if (isSpotifyTrack(currentTrack)) "spotify_toggle_playback" else "play_track"
            onSendEvent(
                actionName,
                buildTrackEventContext(
                    currentTrack,
                    mapOf(
                        "isPlaying" to currentPlaying,
                        "playbackContext" to if (currentPlaying) "now_playing" else "track_result",
                    ),
                ),
            )
        } else {
            onOpenUrl(
                resolveMusicClientJumpUrl(
                    originalSongId = currentTrack.originalId,
                    originalPlaylistId = originalPlaylistId,
                    songId = currentTrack.songId,
                    externalUrl = currentTrack.externalUrl,
                    provider = provider,
                ),
            )
        }
    }

    fun handlePrevious() {
        if (tracks.size > 1) {
            selectTrackAt((safeIndex - 1 + tracks.size) % tracks.size)
        } else if (isSpotifyTrack(currentTrack) || deviceId.isNotBlank()) {
            onSendEvent("spotify_previous", buildTrackEventContext(currentTrack))
        }
    }

    fun handleNext() {
        if (tracks.size > 1) {
            selectTrackAt((safeIndex + 1) % tracks.size)
        } else if (isSpotifyTrack(currentTrack) || deviceId.isNotBlank()) {
            onSendEvent("spotify_next", buildTrackEventContext(currentTrack))
        }
    }

    fun handleToggleLike() {
        val nextLiked = !isLiked
        likedOverrides[safeIndex] = nextLiked
        onUpdateLikedBinding(nextLiked)
        if (currentTrack.songId.isNotBlank()) {
            onSendEvent(
                "toggle_like",
                buildTrackEventContext(currentTrack, mapOf("like" to nextLiked)),
            )
        }
    }

    val cardShape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        animatedPalette.highlight,
                        animatedPalette.mid,
                        animatedPalette.deep,
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 650f),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), cardShape)
            .padding(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左栏：圆角封面 + VIP/试听胶囊 + 底部动态声波条
            CoverArtworkWithWaveform(
                track = currentTrack,
                palette = animatedPalette,
                isPlaying = isPlaying,
                onCoverBitmapLoaded = { bitmap ->
                    extractCoverAmbientPalette(bitmap)?.let { extractedPalette = it }
                },
            )

            // 右栏：顶部操作行 + 歌名/歌手/歌词 + 进度条 + 底部播放三键与歌单弹窗入口
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 132.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 顶部：跳转音乐客户端 App（左） & 红心收藏（右）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallCircleIconButton(
                        onClick = {
                            onOpenUrl(
                                resolveMusicClientJumpUrl(
                                    originalSongId = currentTrack.originalId,
                                    originalPlaylistId = originalPlaylistId,
                                    songId = currentTrack.songId,
                                    externalUrl = currentTrack.externalUrl,
                                    provider = provider,
                                ),
                            )
                        },
                        contentDescription = stringResource(R.string.chat_a2ui_music_open_netease),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.88f),
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    SmallCircleIconButton(
                        onClick = ::handleToggleLike,
                        containerColor = if (isLiked) Color(0xFFFF3B5C).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
                        contentDescription = stringResource(
                            if (isLiked) R.string.chat_a2ui_music_unlike else R.string.chat_a2ui_music_like,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isLiked) Color(0xFFFF3B5C) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // 中部：歌名、歌手/专辑、可选单句歌词
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = currentTrack.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.5.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOf(currentTrack.artist, currentTrack.album)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (currentTrack.lyric.isNotBlank()) {
                        Text(
                            text = currentTrack.lyric,
                            color = animatedPalette.accent.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }

                // 进度条：左正计时 0:51，右倒计时 -1:42
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val remainingMs = (totalDurationMs - clampedPositionMs).coerceAtLeast(0L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatMusicTime(clampedPositionMs),
                            color = Color.White.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        )
                        Text(
                            text = "-" + formatMusicTime(remainingMs),
                            color = Color.White.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        )
                    }
                    MusicProgressBar(
                        progress = progressFraction,
                        enabled = totalDurationMs > 0L && (player != null || isSpotifyTrack(currentTrack) || deviceId.isNotBlank()),
                        onSeek = { ratio ->
                            val targetMs = (totalDurationMs * ratio).roundToLong()
                            positionMs = targetMs
                            if (player != null) {
                                player.seekTo(targetMs)
                            } else if (isSpotifyTrack(currentTrack) || deviceId.isNotBlank()) {
                                onSendEvent(
                                    "spotify_seek",
                                    buildTrackEventContext(currentTrack, mapOf("positionMs" to targetMs)),
                                )
                            }
                        },
                    )
                }

                // 底部播放控制：|<  ( ▶ / ⏸ )  >|  + 可选歌单弹窗入口 ☰
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val canStepQueue = tracks.size > 1 || isSpotifyTrack(currentTrack) || deviceId.isNotBlank()
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(
                                    enabled = canStepQueue,
                                    role = Role.Button,
                                    onClick = ::handlePrevious,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.chat_a2ui_music_previous),
                                tint = Color.White.copy(alpha = if (canStepQueue) 0.92f else 0.32f),
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // 居中白色圆形大播放/暂停按钮
                        Surface(
                            onClick = ::handlePlayPause,
                            shape = CircleShape,
                            color = Color.White,
                            contentColor = Color(0xFF18101C),
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(
                                        if (isPlaying) R.string.chat_a2ui_music_pause else R.string.chat_a2ui_music_play,
                                    ),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable(
                                    enabled = canStepQueue,
                                    role = Role.Button,
                                    onClick = ::handleNext,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.chat_a2ui_music_next),
                                tint = Color.White.copy(alpha = if (canStepQueue) 0.92f else 0.32f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (tracks.size > 1) {
                        SmallCircleIconButton(
                            onClick = { showQueueSheet = true },
                            contentDescription = stringResource(R.string.chat_a2ui_music_open_queue),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQueueSheet && tracks.size > 1) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = animatedPalette.mid,
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            MusicQueueSheetContent(
                playlistTitle = playlistTitle,
                tracks = tracks,
                activeIndex = safeIndex,
                palette = animatedPalette,
                onSelectTrack = { index ->
                    selectTrackAt(index)
                    showQueueSheet = false
                },
                onClose = { showQueueSheet = false },
            )
        }
    }
}

@Composable
private fun CoverArtworkWithWaveform(
    track: MusicTrackItem,
    palette: CoverAmbientPalette,
    isPlaying: Boolean,
    onCoverBitmapLoaded: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val coverShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(coverShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.highlight, palette.mid, palette.deep),
                ),
            )
            .border(1.2.dp, Color.White.copy(alpha = 0.34f), coverShape),
    ) {
        if (track.coverUrl.isNotBlank()) {
            val request = remember(context, track.coverUrl) {
                ImageRequest.Builder(context)
                    .data(track.coverUrl)
                    // 关闭 hardware bitmap 以允许轻量读取封面主色调
                    .allowHardware(false)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    runCatching { state.result.image.toBitmap() }
                        .getOrNull()
                        ?.let(onCoverBitmapLoaded)
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.28f),
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        // 底部暗化渐变遮罩，保证声波纹在亮色封面上依然清晰可见
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.24f),
                            Color.Black.copy(alpha = 0.76f),
                        ),
                    ),
                ),
        )

        val badgeText = when {
            track.isTrial -> stringResource(R.string.chat_a2ui_music_trial_badge)
            track.isVip -> stringResource(R.string.chat_a2ui_music_vip_badge)
            else -> ""
        }
        if (badgeText.isNotEmpty()) {
            Text(
                text = badgeText,
                color = palette.accent,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(99.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun MusicProgressBar(
    progress: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    onSeek(ratio)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        ) {
            widthPx = size.width.coerceAtLeast(1f)
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                size = size,
                cornerRadius = corner,
            )
            val activeWidth = (widthPx * progress.coerceIn(0f, 1f)).coerceAtLeast(0f)
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = Color.White,
                    size = Size(activeWidth, size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@Composable
private fun SmallCircleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    containerColor: Color = Color.White.copy(alpha = 0.10f),
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = Color.White,
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
internal fun MusicQueueSheetContent(
    playlistTitle: String,
    tracks: List<MusicTrackItem>,
    activeIndex: Int,
    palette: CoverAmbientPalette,
    onSelectTrack: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.mid, palette.deep),
                ),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = playlistTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.chat_a2ui_music_queue_count, tracks.size),
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            SmallCircleIconButton(
                onClick = onClose,
                contentDescription = stringResource(R.string.chat_add_to_chat_close),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(tracks) { index, item ->
                val isCurrent = index == activeIndex
                val rowShape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(rowShape)
                        .background(
                            if (isCurrent) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                        )
                        .then(
                            if (isCurrent) {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.20f), rowShape)
                            } else {
                                Modifier
                            },
                        )
                        .clickable { onSelectTrack(index) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (isCurrent) "♪" else (index + 1).toString(),
                        color = if (isCurrent) palette.accent else Color.White.copy(alpha = 0.52f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        ),
                        modifier = Modifier.width(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = if (isCurrent) palette.accent else Color.White,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val artistAlbum = listOf(item.artist, item.album)
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                        if (artistAlbum.isNotBlank()) {
                            Text(
                                text = artistAlbum,
                                color = Color.White.copy(alpha = 0.58f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val tag = when {
                            item.isTrial -> stringResource(R.string.chat_a2ui_music_trial_badge)
                            item.isVip -> stringResource(R.string.chat_a2ui_music_vip_badge)
                            else -> ""
                        }
                        if (tag.isNotEmpty()) {
                            Text(
                                text = tag,
                                color = palette.accent,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        if (item.durationSeconds > 0) {
                            Text(
                                text = formatMusicTime(item.durationSeconds * 1000L),
                                color = Color.White.copy(alpha = 0.56f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 解析音乐客户端跳转链接（同时兼容网易云音乐 `orpheus://` 与 Spotify `spotify:` / `open.spotify.com`）：
 * 1. 若提供了显式 `externalUrl`（`orpheus://`、`spotify:` 或 `http(s)://`），优先规范化使用；
 * 2. 若 `songId` / `provider` 标识为 Spotify（如 `spotify:track:<id>` 或 22 位 Base62 Spotify ID），生成 `spotify:track:<id>`；
 * 3. 网易云场景优先使用纯数字 `originalSongId` 生成 `orpheus://song/<id>`，其次 `orpheus://playlist/<id>`，
 *    绝不将 32 位十六进制加密 ID 拼入客户端路由，兜底唤起 `orpheus://` 首页。
 */
internal fun resolveMusicClientJumpUrl(
    originalSongId: String,
    originalPlaylistId: String = "",
    songId: String = "",
    externalUrl: String = "",
    provider: String = "",
): String {
    val cleanExternal = externalUrl.trim()
    if (cleanExternal.isNotEmpty()) {
        val spotifyWebMatch = Regex("""open\.spotify\.com/(track|album|playlist|artist|episode)/([A-Za-z0-9]+)""")
            .find(cleanExternal)
        if (spotifyWebMatch != null) {
            return "spotify:${spotifyWebMatch.groupValues[1]}:${spotifyWebMatch.groupValues[2]}"
        }
        if (cleanExternal.startsWith("spotify:") ||
            cleanExternal.startsWith("orpheus://") ||
            cleanExternal.startsWith("https://") ||
            cleanExternal.startsWith("http://")
        ) {
            return cleanExternal
        }
    }

    val cleanSongId = songId.trim()
    if (cleanSongId.startsWith("spotify:")) {
        return cleanSongId
    }
    val isHex32 = Regex("^[0-9a-fA-F]{32}$").matches(cleanSongId)
    val isSpotifyBase62 = Regex("^[A-Za-z0-9]{22}$").matches(cleanSongId)
    if (provider.equals("spotify", ignoreCase = true) || (!isHex32 && isSpotifyBase62)) {
        val targetId = cleanSongId.ifBlank { originalSongId.trim() }
        return if (targetId.isNotEmpty()) "spotify:track:$targetId" else "spotify:"
    }

    val songDigits = originalSongId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    if (songDigits != null) return "orpheus://song/$songDigits"
    val playlistDigits = originalPlaylistId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    if (playlistDigits != null) return "orpheus://playlist/$playlistDigits"
    return "orpheus://"
}

/** 保持向后兼容的网易云跳转解析快捷入口。 */
internal fun resolveNeteaseJumpUrl(originalSongId: String, originalPlaylistId: String = ""): String =
    resolveMusicClientJumpUrl(originalSongId = originalSongId, originalPlaylistId = originalPlaylistId)

/**
 * 从封面 [Bitmap] 中通过网格采样提取主色相与主饱和度，并归一化为高对比度的暗色沉浸调色板。
 */
internal fun extractCoverAmbientPalette(bitmap: Bitmap): CoverAmbientPalette? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val stepX = (width / 16).coerceAtLeast(1)
    val stepY = (height / 16).coerceAtLeast(1)
    val buckets = FloatArray(12)
    val hueSum = FloatArray(12)
    val satSum = FloatArray(12)
    val hsl = FloatArray(3)

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha >= 128) {
                ColorUtils.colorToHSL(pixel, hsl)
                val hue = hsl[0]
                val sat = hsl[1]
                val light = hsl[2]
                // 过滤极黑与极白背景噪点，以饱和度加权聚合色相桶
                if (light in 0.08f..0.90f && sat >= 0.12f) {
                    val bucket = ((hue / 30f).toInt()).coerceIn(0, 11)
                    val weight = sat * (1f - abs(light - 0.5f))
                    buckets[bucket] += weight
                    hueSum[bucket] += hue * weight
                    satSum[bucket] += sat * weight
                }
            }
            x += stepX
        }
        y += stepY
    }

    var bestBucket = -1
    var bestWeight = 0f
    for (i in buckets.indices) {
        if (buckets[i] > bestWeight) {
            bestWeight = buckets[i]
            bestBucket = i
        }
    }
    if (bestBucket == -1 || bestWeight <= 0f) return null

    val dominantHue = (hueSum[bestBucket] / bestWeight).coerceIn(0f, 360f)
    val dominantSat = (satSum[bestBucket] / bestWeight).coerceIn(0.35f, 0.85f)

    fun hslColor(h: Float, s: Float, l: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

    return CoverAmbientPalette(
        highlight = hslColor(dominantHue, (dominantSat * 0.95f).coerceIn(0.45f, 0.78f), 0.28f),
        mid = hslColor(dominantHue, (dominantSat * 0.82f).coerceIn(0.38f, 0.65f), 0.15f),
        deep = hslColor(dominantHue, (dominantSat * 0.72f).coerceIn(0.32f, 0.55f), 0.08f),
        accent = hslColor(dominantHue, (dominantSat * 1.10f).coerceIn(0.60f, 0.92f), 0.82f),
    )
}

internal fun fallbackPaletteFor(title: String = "", index: Int = 0): CoverAmbientPalette =
    NeteaseRedAmbientPalette

private fun normalizeSeconds(raw: Double?): Int {
    val number = raw?.takeIf { it > 0 } ?: return 0
    // 兼容模型直接传入网易云或 Spotify 的毫秒（如 213000）或秒（如 213）
    return if (number > 10_000) (number / 1000.0).toInt() else number.toInt()
}

private fun parseTracks(values: CardValues): List<MusicTrackItem> {
    fun itemFrom(row: CardValues): MusicTrackItem = MusicTrackItem(
        title = row.text("title"),
        artist = row.text("artist"),
        album = row.text("album"),
        coverUrl = row.text("coverUrl"),
        audioUrl = row.text("audioUrl"),
        lyric = row.text("lyric"),
        durationSeconds = normalizeSeconds(row.number("durationSeconds")),
        songId = row.text("songId"),
        originalId = row.text("originalId"),
        externalUrl = row.text("externalUrl"),
        liked = row.flag("liked"),
        isTrial = row.flag("isTrial"),
        isVip = row.flag("isVip"),
    )

    val topTrack = itemFrom(values)
    val queue = values.rows("tracks", 50)
        .map(::itemFrom)
        .filter { it.title.isNotBlank() }

    if (queue.isEmpty()) {
        return listOf(topTrack).filter { it.title.isNotBlank() }
    }
    return queue.mapIndexed { idx, track ->
        val matchesTop = (topTrack.songId.isNotBlank() && track.songId == topTrack.songId) ||
            (topTrack.songId.isBlank() && track.title == topTrack.title) ||
            (idx == 0 && queue.none { it.title == topTrack.title })
        if (matchesTop) {
            track.copy(
                audioUrl = track.audioUrl.ifBlank { topTrack.audioUrl },
                coverUrl = track.coverUrl.ifBlank { topTrack.coverUrl },
                lyric = track.lyric.ifBlank { topTrack.lyric },
                durationSeconds = if (track.durationSeconds > 0) track.durationSeconds else topTrack.durationSeconds,
                originalId = track.originalId.ifBlank { topTrack.originalId },
                externalUrl = track.externalUrl.ifBlank { topTrack.externalUrl },
                liked = track.liked || topTrack.liked,
                isTrial = track.isTrial || topTrack.isTrial,
                isVip = track.isVip || topTrack.isVip,
            )
        } else {
            track
        }
    }
}

private fun formatMusicTime(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
