package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.feature.chat.R

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
) {
    val provider = data.provider.ifBlank { stringResource(R.string.chat_player_generic_name) }
    val canOpen = !data.trackUrl.isNullOrBlank()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MusicPlayerArtwork(
                    artworkUrl = data.artworkUrl,
                    playing = data.isPlaying,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ProviderIcon(
                            iconUrl = data.providerIconUrl,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = playerMetadata(provider, data.artist, data.album),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FilledIconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier.size(38.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (data.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (data.isPlaying) R.string.chat_player_pause else R.string.chat_player_play,
                        ),
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.chat_player_next),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!data.deviceName.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Speaker,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = data.deviceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(
                    onClick = onOpenPlayer,
                    enabled = canOpen,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = if (data.provider.isBlank()) {
                            stringResource(R.string.chat_player_open)
                        } else {
                            stringResource(R.string.chat_player_open_named, provider)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (canOpen) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = if (canOpen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerArtwork(artworkUrl: String?, playing: Boolean) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(9.dp))
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

@Composable
private fun playerMetadata(provider: String, artist: String, album: String?): String =
    listOf(provider, artist, album.orEmpty())
        .filter(String::isNotBlank)
        .joinToString(" · ")

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
