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

/** 播放器数据来源，用于决定播放按钮是恢复当前播放还是启动指定曲目。 */
internal enum class MusicPlaybackContext {
    NOW_PLAYING,
    TRACK_RESULT,
    ;

    companion object {
        fun fromWire(value: String?): MusicPlaybackContext =
            if (value.equals("now_playing", ignoreCase = true)) NOW_PLAYING else TRACK_RESULT
    }
}

/**
 * 任意音乐播放器的单曲快照。
 *
 * @property provider 服务展示名称，例如 Spotify、网易云音乐、QQ音乐、Apple Music。
 * @property providerIconUrl 可选的播放器图标 URL；缺失时使用通用音乐图标。
 * @property trackUri 可选的播放器内部曲目标识，播放与遥控操作可复用该值。
 * @property title 曲名。
 * @property artist 艺人或节目名。
 * @property album 专辑或节目名。
 * @property artworkUrl 专辑封面 URL。
 * @property durationMs 可选的曲目总时长，单位毫秒。
 * @property progressMs 可选的快照播放位置，单位毫秒。
 * @property isPlaying 快照时是否正在播放。
 * @property fetchedAtEpochMs 获取该快照的设备时间，用于在 UI 侧外推播放进度。
 * @property deviceId 可选的播放设备 ID，用于把遥控操作发往同一设备。
 * @property deviceName 可选的播放设备名称。
 * @property trackUrl 可选的曲目网页地址，用于打开对应播放器。
 * @property playbackContext 快照来源；当前播放用于恢复，曲目搜索结果用于首次播放。
 */
internal data class MusicPlayerData(
    val provider: String,
    val providerIconUrl: String?,
    val trackUri: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val progressMs: Long?,
    val isPlaying: Boolean,
    val fetchedAtEpochMs: Long,
    val deviceId: String?,
    val deviceName: String?,
    val trackUrl: String?,
    val playbackContext: MusicPlaybackContext,
    /** 服务确认的收藏状态；未知时不展示虚假的已收藏状态。 */
    val isSaved: Boolean? = null,
    /** 服务返回的随机播放状态。 */
    val shuffleEnabled: Boolean? = null,
    /** off、context 或 track；未知时不推测循环模式。 */
    val repeatMode: String? = null,
    /** 当前工具与设备是否支持对应操作；缺失时禁用新增控制。 */
    val canSeek: Boolean = false,
    val canPrevious: Boolean = false,
    val canShuffle: Boolean = false,
    val canRepeat: Boolean = false,
    val canSave: Boolean = false,
) {
    /** 根据快照时间外推当前播放位置；缺少时长或暂停时返回可用位置。 */
    fun positionAt(nowEpochMs: Long): Long? {
        val duration = durationMs ?: return null
        val base = (progressMs ?: 0L).coerceIn(0L, duration)
        // 没有稳定快照时间时只显示静态位置，绝不能在每次重组时把基准改成“现在”，
        // 否则 Lazy 列表回收重入会让进度回到 progressMs。
        if (!isPlaying || fetchedAtEpochMs <= 0L) return base
        val elapsed = (nowEpochMs - fetchedAtEpochMs).coerceAtLeast(0L)
        return (base + elapsed).coerceAtMost(duration)
    }

    companion object {
        fun fromA2ui(value: Any): MusicPlayerData? {
            if (value is MusicPlayerData) return value
            val map = value as? Map<*, *> ?: return null
            val title = map.string("title") ?: return null
            val durationMs = map.long("durationMs")?.takeIf { it > 0 }
            val progressMs = durationMs?.let { map.long("progressMs")?.coerceIn(0L, it) }
            return MusicPlayerData(
                provider = map.string("provider").orEmpty(),
                providerIconUrl = map.string("providerIconUrl"),
                trackUri = map.string("trackUri"),
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
                playbackContext = MusicPlaybackContext.fromWire(map.string("playbackContext")),
                isSaved = map.boolean("isSaved"),
                shuffleEnabled = map.boolean("shuffleEnabled"),
                repeatMode = map.string("repeatMode")?.takeIf { it in setOf("off", "context", "track") },
                canSeek = map.boolean("canSeek") == true,
                canPrevious = map.boolean("canPrevious") == true,
                canShuffle = map.boolean("canShuffle") == true,
                canRepeat = map.boolean("canRepeat") == true,
                canSave = map.boolean("canSave") == true,
            )
        }
    }
}

private val musicPlayerSchema = A2uiObjectSchema(
    description = "Music player snapshot for any provider. Map available fields exactly; " +
        "never invent progress, duration, device, artwork, or track identity.",
    properties = mapOf(
        "isSaved" to A2uiBooleanSchema("Verified library membership; omit if unknown."),
        "shuffleEnabled" to A2uiBooleanSchema("Verified shuffle state; omit if unknown."),
        "repeatMode" to A2uiStringSchema("Verified repeat mode: off, context, track."),
        "canSeek" to A2uiBooleanSchema("True only when tools and current device allow seeking."),
        "canPrevious" to A2uiBooleanSchema("True only when tools and current device allow previous."),
        "canShuffle" to A2uiBooleanSchema("True only when tools and current device allow shuffle."),
        "canRepeat" to A2uiBooleanSchema("True only when tools and current device allow repeat."),
        "canSave" to A2uiBooleanSchema("True only when library check/save/remove tools are available."),
        "provider" to A2uiStringSchema(
            "Human-readable service name, for example Spotify, 网易云音乐, QQ音乐, or Apple Music.",
        ),
        "providerIconUrl" to A2uiStringSchema("Optional provider icon URL."),
        "trackUri" to A2uiStringSchema("Optional provider-specific track URI or id."),
        "title" to A2uiStringSchema("Track title."),
        "artist" to A2uiStringSchema("Artists or hosts joined from the provider result."),
        "album" to A2uiStringSchema("Album, playlist, or program name."),
        "artworkUrl" to A2uiStringSchema("Cover artwork URL."),
        "durationMs" to A2uiNumberSchema("Optional duration in milliseconds."),
        "progressMs" to A2uiNumberSchema("Optional playback progress in milliseconds."),
        "isPlaying" to A2uiBooleanSchema("Whether the snapshot is playing."),
        "fetchedAtEpochMs" to A2uiNumberSchema(
            "Device epoch milliseconds when the playback snapshot was fetched.",
        ),
        "deviceId" to A2uiStringSchema("Optional playback device id."),
        "deviceName" to A2uiStringSchema("Optional playback device name."),
        "trackUrl" to A2uiStringSchema("Optional provider web URL for the track."),
        "playbackContext" to A2uiStringSchema(
            "now_playing for current playback, or track_result for a search result.",
        ),
    ),
    required = setOf("title"),
)

/**
 * 任意音乐播放器共用的 A2UI 属性定义。
 *
 * 属性允许内联快照对象、数据模型绑定或函数调用；schema 明确记录通用字段，
 * 避免模型把某个服务的字段或播放进度强行套到其他播放器上。
 */
internal val MusicPlayerA2uiProperty = A2uiProperty.dynamicCustom(
    key = "player",
    schema = A2uiAnySchema(
        description = "Music player snapshot or a dynamic binding such as " +
            "{\"path\":\"/player\"}.",
        keywords = listOf(
            A2uiSchemaKeyword.OneOf(
                listOf(
                    musicPlayerSchema,
                    A2uiDataBindingSchema.DEFAULT_INSTANCE,
                    A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                ),
            ),
        ),
    ),
    safeCast = { MusicPlayerData.fromA2ui(it) },
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
