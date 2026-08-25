package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.toJsonNative
import org.json.JSONArray
import org.json.JSONObject

internal fun libraryTools(api: SpotifyApi): List<BaseTool> = listOf(
    SpotifyNowPlayingTool(api),
    SpotifyMyPlaylistsTool(api),
    SpotifyGetPlaylistTool(api),
    SpotifyGetPlaylistTracksTool(api),
    SpotifySavedTracksTool(api),
    SpotifyRecentlyPlayedTool(api),
    SpotifyQueueTool(api),
    SpotifyDevicesTool(api),
    SpotifyTopTracksTool(api),
    SpotifyTopArtistsTool(api),
)

/** spotify_now_playing — 当前播放内容。 */
private class SpotifyNowPlayingTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/currently-playing")
        return if (json == null) {
            mapOf(SpotifyTool.RESULT_KEY to "当前没有正在播放的内容")
        } else {
            mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
        }
    }

    companion object {
        const val NAME: String = "spotify_now_playing"
        const val DESCRIPTION: String = "查看当前正在播放的曲目及播放进度、设备、音量等信息。"
    }
}

/** spotify_get_my_playlists — 当前用户的歌单列表。 */
private class SpotifyMyPlaylistsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50"),
                "offset" to Schema(type = Type.INTEGER, description = "分页偏移，默认 0"),
            ),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get(
            "/me/playlists",
            mapOf("limit" to intArg(args, "limit", 20), "offset" to intArg(args, "offset", 0)),
        )
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_my_playlists"
        const val DESCRIPTION: String = "获取当前用户的歌单列表（含名称、曲目数、ID）。"
    }
}

/** spotify_get_playlist — 单个歌单详情。 */
private class SpotifyGetPlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("playlist_id" to Schema(type = Type.STRING, description = "Spotify 歌单 ID")),
            required = listOf("playlist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "playlist_id") ?: throw IllegalStateException("缺少参数 playlist_id")
        val json = api.get("/playlists/$id") ?: throw IllegalStateException("未找到该歌单")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_playlist"
        const val DESCRIPTION: String = "获取歌单详情（名称、所有者、曲目数、公开状态、描述）。"
    }
}

/** spotify_get_playlist_tracks — 歌单内曲目列表（2026 迁移后用 /items 端点）。 */
private class SpotifyGetPlaylistTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "Spotify 歌单 ID"),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 50，最大 50"),
                "offset" to Schema(type = Type.INTEGER, description = "分页偏移，默认 0"),
            ),
            required = listOf("playlist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "playlist_id") ?: throw IllegalStateException("缺少参数 playlist_id")
        val json = api.get(
            "/playlists/$id/items",
            mapOf(
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
                "additional_types" to "track,episode",
            ),
        )
        // 新响应每条目把曲目放在 `item`，旧版放 `track`，统一投影出来。
        val items = json?.optJSONArray("items") ?: JSONArray()
        val projected = JSONArray()
        for (i in 0 until items.length()) {
            val entry = items.optJSONObject(i)
            val track = entry?.optJSONObject("item") ?: entry?.optJSONObject("track")
            projected.put(track ?: JSONObject.NULL)
        }
        return mapOf(SpotifyTool.RESULT_KEY to projected.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_playlist_tracks"
        const val DESCRIPTION: String = "获取歌单内的曲目列表（含曲目 ID）。"
    }
}

/** spotify_get_saved_tracks — 当前用户收藏的歌曲（Liked Songs）。 */
private class SpotifySavedTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50"),
                "offset" to Schema(type = Type.INTEGER, description = "分页偏移，默认 0"),
            ),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get(
            "/me/tracks",
            mapOf("limit" to intArg(args, "limit", 20), "offset" to intArg(args, "offset", 0)),
        )
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_saved_tracks"
        const val DESCRIPTION: String = "获取当前用户收藏（Liked Songs）的歌曲列表。"
    }
}

/** spotify_get_recently_played — 最近播放记录。 */
private class SpotifyRecentlyPlayedTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/recently-played", mapOf("limit" to intArg(args, "limit", 20)))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_recently_played"
        const val DESCRIPTION: String = "获取最近播放过的歌曲记录（含播放时间）。"
    }
}

/** spotify_get_queue — 当前播放队列。 */
private class SpotifyQueueTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/queue")
            ?: throw IllegalStateException("获取队列失败")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_queue"
        const val DESCRIPTION: String = "查看当前播放队列（正在播放 + 接下来）。"
    }
}

/** spotify_get_devices — 可用的 Spotify Connect 设备。 */
private class SpotifyDevicesTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/devices")
            ?: throw IllegalStateException("获取设备列表失败")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_devices"
        const val DESCRIPTION: String = "列出当前用户的 Spotify Connect 设备（名称、类型、是否活动、音量）。"
    }
}

/** spotify_get_top_tracks — 用户常听的歌曲（收听统计）。 */
private class SpotifyTopTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "time_range" to Schema(
                    type = Type.STRING,
                    description = "时间范围：short_term(近4周) / medium_term(近6月) / long_term(近1年)，默认 medium_term",
                    enum = listOf("short_term", "medium_term", "long_term"),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50"),
            ),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get(
            "/me/top/tracks",
            mapOf(
                "time_range" to (strArg(args, "time_range") ?: "medium_term"),
                "limit" to intArg(args, "limit", 20),
            ),
        )
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_top_tracks"
        const val DESCRIPTION: String = "获取当前用户常听的歌曲排名（收听统计）。"
    }
}

/** spotify_get_top_artists — 用户常听的歌手（收听统计）。 */
private class SpotifyTopArtistsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "time_range" to Schema(
                    type = Type.STRING,
                    description = "时间范围：short_term(近4周) / medium_term(近6月) / long_term(近1年)，默认 medium_term",
                    enum = listOf("short_term", "medium_term", "long_term"),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50"),
            ),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get(
            "/me/top/artists",
            mapOf(
                "time_range" to (strArg(args, "time_range") ?: "medium_term"),
                "limit" to intArg(args, "limit", 20),
            ),
        )
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_top_artists"
        const val DESCRIPTION: String = "获取当前用户常听的歌手排名（收听统计）。"
    }
}
