package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import github.ponyhuang.gimi.feature.chat.R

// Spotify 品牌色仅用于品牌标识和播放主操作；其余层级继续使用宿主 M3 token。
private val SpotifyGreen = Color(0xFF1ED760)
private val SpotifyOnGreen = Color(0xFF071B0C)

/** Spotify 原生遥控播放器；所有业务操作通过 A2UI event 交给主 Agent。 */
internal class ChatA2uiSpotifyPlayer(
    private val openUrl: (String) -> Unit,
) : A2uiComponent {
    override val name = "SpotifyPlayer"
    override val description =
        "Compact Spotify now-playing or track card. Use as the root for Spotify playback and " +
            "now-playing information. Set the required spotify property from spotify_now_playing " +
            "or a Spotify track result; never rebuild this layout from Card/Text/List."
    override val properties = listOf(SpotifyPlayerA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(SpotifyPlayerA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(
        properties: A2uiComponentProperties,
        modifier: Modifier,
    ) {
        val data = properties.bind(SpotifyPlayerA2uiProperty) ?: return
        SpotifyPlayerCard(
            data = data,
            onTogglePlayback = {
                dispatchAction(
                    spotifyPlayerAction(
                        name = "spotify_toggle_playback",
                        context = buildMap {
                            put("trackUri", data.trackUri)
                            put("isPlaying", data.isPlaying)
                            put("playbackContext", data.playbackContext.name.lowercase())
                            data.deviceId?.let { put("deviceId", it) }
                        },
                    ),
                )
            },
            onNext = {
                dispatchAction(
                    spotifyPlayerAction(
                        name = "spotify_next",
                        context = buildMap { data.deviceId?.let { put("deviceId", it) } },
                    ),
                )
            },
            onOpenSpotify = {
                data.trackUrl?.takeIf(String::isNotBlank)?.let(openUrl)
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun SpotifyPlayerCard(
    data: SpotifyPlayerData,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onOpenSpotify: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                SpotifyMiniArtwork(
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
                        Image(
                            painter = painterResource(R.drawable.ic_spotify),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = spotifyArtistAlbum(artist = data.artist, album = data.album),
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
                        containerColor = SpotifyGreen,
                        contentColor = SpotifyOnGreen,
                    ),
                ) {
                    Icon(
                        imageVector = if (data.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (data.isPlaying) R.string.chat_spotify_pause else R.string.chat_spotify_play,
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
                        contentDescription = stringResource(R.string.chat_spotify_next),
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
                Icon(
                    imageVector = Icons.Rounded.Speaker,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = data.deviceName?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.chat_spotify_connect_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onOpenSpotify,
                    enabled = canOpen,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_spotify_open),
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
                        tint = if (canOpen) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }

        }
    }
}

@Composable
private fun SpotifyMiniArtwork(artworkUrl: String?, playing: Boolean) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        SpotifyGreen.copy(alpha = 0.90f),
                        SpotifyOnGreen,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(21.dp),
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = stringResource(R.string.chat_spotify_artwork),
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
                    .background(SpotifyGreen, CircleShape),
            )
        }
    }
}

@Composable
private fun spotifyArtistAlbum(artist: String, album: String?): String =
    if (album.isNullOrBlank()) {
        artist.ifBlank { "Spotify" }
    } else if (artist.isBlank()) {
        album
    } else {
        stringResource(R.string.chat_spotify_artist_album, artist, album)
    }

/** 构造标准 A2UI server event，供主 Agent 调用 Spotify 工具。 */
internal fun spotifyPlayerAction(
    name: String,
    context: Map<String, Any?>,
): Map<String, Any?> = mapOf(
    "event" to mapOf(
        "name" to name,
        "context" to context.filterValues { it != null },
    ),
)
