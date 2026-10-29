package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.feature.chat.R
import kotlinx.coroutines.delay

/** 与播放器品牌无关的原生遥控卡片；所有业务操作通过 A2UI event 交给主 Agent。 */
// 组件只依赖通用播放器字段；具体服务由 provider 与主 Agent 的工具路由决定。
internal class ChatA2uiMusicPlayer(
    private val openUrl: (String) -> Unit,
) : A2uiComponent {
    override val name = "MusicPlayer"
    override val description =
        "Compact music player card for any provider, such as Spotify, NetEase Cloud Music, " +
                "QQ Music, Apple Music, or a local player. Use as the root for now-playing or track " +
                "results. Set the required `player` property; never rebuild this layout from " +
                "Card/Text/List."
    override val properties = listOf(MusicPlayerA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(MusicPlayerA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val data = properties.bind(MusicPlayerA2uiProperty) ?: return
        MusicPlayerCard(
            data = data,
            onTogglePlayback = {
                dispatchAction(
                    musicPlayerAction(
                        name = "player_toggle_playback",
                        context = buildMap {
                            data.provider.takeIf(String::isNotBlank)?.let { put("provider", it) }
                            put("title", data.title)
                            data.artist.takeIf(String::isNotBlank)?.let { put("artist", it) }
                            data.trackUri?.let { put("trackUri", it) }
                            data.trackUrl?.let { put("trackUrl", it) }
                            put("isPlaying", data.isPlaying)
                            put("playbackContext", data.playbackContext.name.lowercase())
                            data.deviceId?.let { put("deviceId", it) }
                        },
                    ),
                )
            },
            onNext = {
                dispatchAction(
                    musicPlayerAction(
                        name = "player_next",
                        context = buildMap {
                            data.provider.takeIf(String::isNotBlank)?.let { put("provider", it) }
                            data.deviceId?.let { put("deviceId", it) }
                        },
                    ),
                )
            },
            onOpenPlayer = {
                data.trackUrl?.takeIf(String::isNotBlank)?.let(openUrl)
            },
            onCommand = { name, arguments ->
                dispatchAction(musicPlayerAction(name, buildMap {
                    put("provider", data.provider)
                    data.deviceId?.let { put("deviceId", it) }
                    data.trackUri?.let { put("trackUri", it) }
                    data.trackUrl?.let { put("trackUrl", it) }
                    putAll(arguments)
                }))
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun MusicPlayerCard(
    data: MusicPlayerData,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onCommand: (String, Map<String, Any?>) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val provider = data.provider.ifBlank { stringResource(R.string.chat_player_generic_name) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
    ) {
        // 不使用固定卡片高度，长标题和大字体可以自然撑开，避免控制栏被挤压。
        Column(Modifier
            .fillMaxWidth()
            .padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MusicPlayerArtwork(data.artworkUrl, data.isPlaying)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(Modifier
                            .size(5.dp)
                            .background(colors.primary, CircleShape))
                        Text(
                            stringResource(if (data.isPlaying) R.string.chat_player_playing else R.string.chat_player_paused),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        data.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (data.artist.isNotBlank()) {
                        Text(
                            data.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (data.canSave && !data.trackUri.isNullOrBlank()) {
                        Surface(
                            onClick = {
                                onCommand(
                                    "player_set_saved",
                                    mapOf("saved" to (data.isSaved != true))
                                )
                            },
                            enabled = data.isSaved != null,
                            shape = CircleShape, color = colors.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    if (data.isSaved == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    null,
                                    Modifier.size(14.dp),
                                    tint = colors.primary
                                )
                                Text(
                                    stringResource(if (data.isSaved == true) R.string.chat_player_saved else R.string.chat_player_save),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!data.deviceName.isNullOrBlank()) {
                    Icon(
                        Icons.Rounded.Speaker,
                        null,
                        Modifier.size(14.dp),
                        tint = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    data.deviceName.orEmpty(),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = onOpenPlayer,
                    enabled = !data.trackUrl.isNullOrBlank(),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    ProviderIcon(data.providerIconUrl, Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        provider,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.OpenInNew,
                        stringResource(R.string.chat_player_open_named, provider),
                        Modifier.size(13.dp)
                    )
                }
            }
            MusicPlayerProgress(data) { onCommand("player_seek", mapOf("positionMs" to it)) }
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 250.dp
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .requiredHeightIn(min = 60.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (!compact) MusicPlayerShuffleButton(data, onCommand)
                        IconButton(
                            onClick = { onCommand("player_previous", emptyMap()) },
                            enabled = data.canPrevious,
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                stringResource(R.string.chat_player_previous)
                            )
                        }
                        FilledIconButton(
                            onClick = onTogglePlayback,
                            modifier = Modifier.size(58.dp)
                        ) {
                            Icon(
                                if (data.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                stringResource(if (data.isPlaying) R.string.chat_player_pause else R.string.chat_player_play),
                                Modifier.size(30.dp),
                            )
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Rounded.SkipNext, stringResource(R.string.chat_player_next))
                        }
                        if (!compact) MusicPlayerRepeatButton(data, onCommand)
                    }
                    if (compact) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MusicPlayerShuffleButton(data, onCommand)
                            MusicPlayerRepeatButton(data, onCommand)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerShuffleButton(
    data: MusicPlayerData,
    onCommand: (String, Map<String, Any?>) -> Unit
) {
    IconButton(
        onClick = {
            onCommand(
                "player_set_shuffle",
                mapOf("enabled" to (data.shuffleEnabled != true))
            )
        },
        enabled = data.canShuffle && data.shuffleEnabled != null,
    ) {
        Icon(
            Icons.Rounded.Shuffle,
            stringResource(R.string.chat_player_shuffle),
            tint = if (data.shuffleEnabled == true) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}

@Composable
private fun MusicPlayerRepeatButton(
    data: MusicPlayerData,
    onCommand: (String, Map<String, Any?>) -> Unit
) {
    IconButton(
        onClick = {
            val next = when (data.repeatMode) {
                "off" -> "context"
                "context" -> "track"
                else -> "off"
            }
            onCommand("player_set_repeat", mapOf("mode" to next))
        },
        enabled = data.canRepeat && data.repeatMode != null,
    ) {
        Icon(
            if (data.repeatMode == "track") Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            stringResource(R.string.chat_player_repeat),
            tint = if (data.repeatMode != null && data.repeatMode != "off") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Unspecified,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicPlayerProgress(data: MusicPlayerData, onSeek: (Long) -> Unit) {
    var now by remember(data) { mutableLongStateOf(System.currentTimeMillis()) }
    var dragging by remember(data) { mutableStateOf<Float?>(null) }
    // 只外推快照进度，不轮询服务；曲目结束时停在末尾，不擅自切歌。
    LaunchedEffect(data) {
        while (data.isPlaying && data.durationMs != null && data.fetchedAtEpochMs > 0) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val duration = data.durationMs
    val position = dragging?.toLong() ?: data.positionAt(now)
    val enabled =
        data.canSeek && data.playbackContext == MusicPlaybackContext.NOW_PLAYING && duration != null && data.progressMs != null
    val colors = MaterialTheme.colorScheme
    Slider(
        value = dragging ?: (position ?: 0L).toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            // 松手后仅派发一次工具事件，不在拖动中连续发送付费/远程请求。
            dragging?.let { onSeek(it.toLong().coerceIn(0L, duration ?: 0L)) }
            dragging = null
        },
        enabled = enabled,
        valueRange = 0f..(duration ?: 1L).toFloat(),
        modifier = Modifier.fillMaxWidth(),
        thumb = {
            Box(
                Modifier
                    .size(14.dp)
                    .background(
                        if (enabled) colors.primary else colors.onSurfaceVariant,
                        CircleShape
                    )
            )
        },
        track = { state ->
            Canvas(Modifier
                .fillMaxWidth()
                .height(4.dp)) {
                val fraction = (state.value / (duration ?: 1L).toFloat()).coerceIn(0f, 1f)
                val start = Offset(0f, size.height / 2)
                drawLine(
                    colors.surfaceContainerHighest,
                    start,
                    Offset(size.width, start.y),
                    size.height,
                    StrokeCap.Round
                )
                if (fraction > 0f) drawLine(
                    colors.primary,
                    start,
                    Offset(size.width * fraction, start.y),
                    size.height,
                    StrokeCap.Round
                )
            }
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            playerTime(position),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
        Text(
            if (duration != null && position != null) "−" + playerTime(
                (duration - position).coerceAtLeast(
                    0L
                )
            ) else "—", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
        )
    }
}

private fun playerTime(milliseconds: Long?): String {
    if (milliseconds == null) return "—"
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun MusicPlayerArtwork(artworkUrl: String?, playing: Boolean) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp),
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = stringResource(R.string.chat_player_artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (playing) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun ProviderIcon(iconUrl: String?, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(),
        )
        if (!iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 构造标准 A2UI server event，供主 Agent 路由到具体播放器工具。 */
internal fun musicPlayerAction(
    name: String,
    context: Map<String, Any?>,
): Map<String, Any?> = mapOf(
    "event" to mapOf(
        "name" to name,
        "context" to context.filterValues { it != null },
    ),
)
