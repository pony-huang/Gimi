package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiBooleanSchema
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema

/** Spotify 播放器数据来源，用于决定播放按钮是恢复当前播放还是启动指定曲目。 */
internal enum class SpotifyPlaybackContext {
    NOW_PLAYING,
    TRACK_RESULT,
    ;

    companion object {
        fun fromWire(value: String?): SpotifyPlaybackContext =
            if (value.equals("now_playing", ignoreCase = true)) NOW_PLAYING else TRACK_RESULT
    }
}

/**
 * Spotify 原生播放器所需的单曲快照。
 *
 * @property trackUri Spotify 曲目 URI，播放与遥控操作必须复用该值。
 * @property title 曲名。
 * @property artist 艺人或节目名。
 * @property album 专辑或节目名。
 * @property artworkUrl 专辑封面 URL。
 * @property durationMs 曲目总时长，单位毫秒。
 * @property progressMs 快照时的播放位置，单位毫秒。
 * @property isPlaying 快照时是否正在播放。
 * @property fetchedAtEpochMs 获取该快照的设备时间，用于在 UI 侧外推播放进度。
 * @property deviceId Spotify Connect 设备 ID，用于把遥控操作发往同一设备。
 * @property deviceName Spotify Connect 设备名称。
 * @property trackUrl Spotify 曲目网页地址，用于打开 Spotify。
 * @property playbackContext 快照来源；当前播放用于恢复，曲目搜索结果用于首次播放。
 */
internal data class SpotifyPlayerData(
    val trackUri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val progressMs: Long,
    val isPlaying: Boolean,
    val fetchedAtEpochMs: Long,
    val deviceId: String?,
    val deviceName: String?,
    val trackUrl: String?,
    val playbackContext: SpotifyPlaybackContext,
) {
    /** 根据快照时间外推当前播放位置；暂停时停留在快照位置。 */
    fun positionAt(nowEpochMs: Long): Long {
        val base = progressMs.coerceIn(0L, durationMs)
        // 没有稳定快照时间时只显示静态位置，绝不能在每次重组时把基准改成“现在”，
        // 否则 Lazy 列表回收重入会让进度回到 progressMs。
        if (!isPlaying || fetchedAtEpochMs <= 0L) return base
        val elapsed = (nowEpochMs - fetchedAtEpochMs).coerceAtLeast(0L)
        return (base + elapsed).coerceAtMost(durationMs)
    }

    companion object {
        fun fromA2ui(value: Any): SpotifyPlayerData? {
            if (value is SpotifyPlayerData) return value
            val map = value as? Map<*, *> ?: return null
            val trackUri = map.string("trackUri") ?: return null
            val title = map.string("title") ?: return null
            val durationMs = map.long("durationMs")?.takeIf { it > 0 } ?: return null
            val progressMs = map.long("progressMs")?.coerceIn(0L, durationMs) ?: 0L
            return SpotifyPlayerData(
                trackUri = trackUri,
                title = title,
                artist = map.string("artist").orEmpty(),
                album = map.string("album"),
                artworkUrl = map.string("artworkUrl"),
                durationMs = durationMs,
                progressMs = progressMs,
                isPlaying = map.boolean("isPlaying") ?: false,
                fetchedAtEpochMs = map.long("fetchedAtEpochMs")?.takeIf { it > 0L } ?: 0L,
                deviceId = map.string("deviceId"),
                deviceName = map.string("deviceName"),
                trackUrl = map.string("trackUrl"),
                playbackContext = SpotifyPlaybackContext.fromWire(map.string("playbackContext")),
            )
        }
    }
}

private val spotifyPlayerSchema = A2uiObjectSchema(
    description = "Spotify player snapshot. Map Spotify API fields exactly; never invent progress.",
    properties = mapOf(
        "trackUri" to A2uiStringSchema("Spotify item URI, for example spotify:track:..."),
        "title" to A2uiStringSchema("Track title from item.name."),
        "artist" to A2uiStringSchema("Artists joined from item.artists[].name."),
        "album" to A2uiStringSchema("Album name from item.album.name."),
        "artworkUrl" to A2uiStringSchema("Album image URL."),
        "durationMs" to A2uiNumberSchema("Duration from item.duration_ms."),
        "progressMs" to A2uiNumberSchema("Progress from the currently-playing progress_ms."),
        "isPlaying" to A2uiBooleanSchema("Whether the snapshot is playing."),
        "fetchedAtEpochMs" to A2uiNumberSchema(
            "Device epoch milliseconds when spotify_now_playing returned the snapshot.",
        ),
        "deviceId" to A2uiStringSchema("Spotify Connect device id."),
        "deviceName" to A2uiStringSchema("Spotify Connect device name."),
        "trackUrl" to A2uiStringSchema("Spotify external URL from item.external_urls.spotify."),
        "playbackContext" to A2uiStringSchema(
            "now_playing for current playback, or track_result for a track search result.",
        ),
    ),
    required = setOf("trackUri", "title", "durationMs"),
)

/**
 * Spotify 播放器共用的 A2UI 属性定义。
 *
 * 属性允许内联快照对象、数据模型绑定或函数调用；schema 明确记录 Spotify API
 * 到 UI 字段的映射，避免模型自行猜测进度、时长和设备信息。
 */
internal val SpotifyPlayerA2uiProperty = A2uiProperty.dynamicCustom(
    key = "spotify",
    schema = A2uiAnySchema(
        description = "Spotify playback snapshot or a dynamic binding such as " +
            "{\"path\":\"/spotify\"}.",
        keywords = listOf(
            A2uiSchemaKeyword.OneOf(
                listOf(
                    spotifyPlayerSchema,
                    A2uiDataBindingSchema.DEFAULT_INSTANCE,
                    A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                ),
            ),
        ),
    ),
    safeCast = { SpotifyPlayerData.fromA2ui(it) },
    required = true,
)

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)

private fun Map<*, *>.long(key: String): Long? = when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.trim().toLongOrNull()
    else -> null
}

private fun Map<*, *>.boolean(key: String): Boolean? = when (val value = this[key]) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.toBooleanStrictOrNull()
    else -> null
}
