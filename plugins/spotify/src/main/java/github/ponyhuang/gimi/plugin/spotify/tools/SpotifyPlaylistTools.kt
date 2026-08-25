package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.toJsonNative
import org.json.JSONArray
import org.json.JSONObject

internal fun playlistTools(api: SpotifyApi): List<BaseTool> = listOf(
    SpotifyCreatePlaylistTool(api),
    SpotifyAddTracksToPlaylistTool(api),
    SpotifyRemoveTracksFromPlaylistTool(api),
    SpotifyReorderPlaylistItemsTool(api),
    SpotifyUpdatePlaylistTool(api),
)

/** 普通 ID 统一成 spotify:track:{id} URI；已是 URI 则原样返回。 */
private fun spotifyTrackUri(id: String): String =
    if (id.startsWith("spotify:")) id else "spotify:track:$id"

/** spotify_create_playlist — 创建歌单。 */
private class SpotifyCreatePlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "name" to Schema(type = Type.STRING, description = "歌单名称，不能为空"),
                "description" to Schema(type = Type.STRING, description = "歌单描述（可选）"),
                "public" to Schema(type = Type.BOOLEAN, description = "是否公开，默认 false"),
            ),
            required = listOf("name"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val name = strArg(args, "name") ?: throw IllegalStateException("缺少参数 name")
        val body = JSONObject()
            .put("name", name)
            .put("public", boolArg(args, "public", false))
        strArg(args, "description")?.let { body.put("description", it) }
        val json = api.post("/me/playlists", body = body) ?: throw IllegalStateException("创建歌单失败")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_create_playlist"
        const val DESCRIPTION: String = "创建新的 Spotify 歌单。"
    }
}

/** spotify_add_tracks_to_playlist — 添加歌曲到歌单。 */
private class SpotifyAddTracksToPlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "目标歌单 ID"),
                "track_ids" to Schema(
                    type = Type.ARRAY,
                    description = "要添加的曲目 ID 或 URI 列表",
                    items = Schema(type = Type.STRING),
                ),
                "position" to Schema(type = Type.INTEGER, description = "插入位置（0 起），缺省追加到末尾"),
            ),
            required = listOf("playlist_id", "track_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("缺少参数 playlist_id")
        val uris = listArg(args, "track_ids").map(::spotifyTrackUri)
        if (uris.isEmpty()) throw IllegalStateException("track_ids 不能为空")
        val body = JSONObject().put("uris", JSONArray().apply { uris.forEach(::put) })
        intArg(args, "position", -1).takeIf { it >= 0 }?.let { body.put("position", it) }
        api.post("/playlists/$playlistId/items", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "已向歌单添加 ${uris.size} 首")
    }

    companion object {
        const val NAME: String = "spotify_add_tracks_to_playlist"
        const val DESCRIPTION: String = "向歌单添加一首或多首歌曲。"
    }
}

/** spotify_remove_tracks_from_playlist — 从歌单移除歌曲。 */
private class SpotifyRemoveTracksFromPlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "目标歌单 ID"),
                "track_ids" to Schema(
                    type = Type.ARRAY,
                    description = "要移除的曲目 ID 或 URI 列表（最多 100）",
                    items = Schema(type = Type.STRING),
                ),
                "snapshot_id" to Schema(type = Type.STRING, description = "歌单快照 ID（可选，用于并发保护）"),
            ),
            required = listOf("playlist_id", "track_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("缺少参数 playlist_id")
        val uris = listArg(args, "track_ids").take(100).map(::spotifyTrackUri)
        if (uris.isEmpty()) throw IllegalStateException("track_ids 不能为空")
        val items = JSONArray().apply {
            uris.forEach { id -> put(JSONObject().put("uri", id)) }
        }
        val body = JSONObject().put("items", items)
        strArg(args, "snapshot_id")?.let { body.put("snapshot_id", it) }
        api.delete("/playlists/$playlistId/items", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "已从歌单移除 ${uris.size} 首")
    }

    companion object {
        const val NAME: String = "spotify_remove_tracks_from_playlist"
        const val DESCRIPTION: String = "从歌单移除一首或多首歌曲。"
    }
}

/** spotify_reorder_playlist_items — 重排歌单内曲目顺序。 */
private class SpotifyReorderPlaylistItemsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "目标歌单 ID"),
                "range_start" to Schema(type = Type.INTEGER, description = "要移动的第一项位置（0 起）"),
                "insert_before" to Schema(type = Type.INTEGER, description = "插入到的位置（0 起）"),
                "range_length" to Schema(type = Type.INTEGER, description = "移动的连续项数，默认 1"),
                "snapshot_id" to Schema(type = Type.STRING, description = "歌单快照 ID（可选）"),
            ),
            required = listOf("playlist_id", "range_start", "insert_before"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("缺少参数 playlist_id")
        val body = JSONObject()
            .put("range_start", intArg(args, "range_start", 0))
            .put("insert_before", intArg(args, "insert_before", 0))
        intArg(args, "range_length", -1).takeIf { it >= 0 }?.let { body.put("range_length", it) }
        strArg(args, "snapshot_id")?.let { body.put("snapshot_id", it) }
        api.put("/playlists/$playlistId/items", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "已重排歌单内曲目顺序")
    }

    companion object {
        const val NAME: String = "spotify_reorder_playlist_items"
        const val DESCRIPTION: String = "重排歌单内一段曲目的顺序。"
    }
}

/** spotify_update_playlist — 更新歌单信息。 */
private class SpotifyUpdatePlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "目标歌单 ID"),
                "name" to Schema(type = Type.STRING, description = "新名称（可选）"),
                "description" to Schema(type = Type.STRING, description = "新描述（可选）"),
                "public" to Schema(type = Type.BOOLEAN, description = "是否公开（可选）"),
                "collaborative" to Schema(type = Type.BOOLEAN, description = "是否协作歌单（需非公开）（可选）"),
            ),
            required = listOf("playlist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("缺少参数 playlist_id")
        val body = JSONObject()
        strArg(args, "name")?.let { body.put("name", it) }
        (args["description"] as? String)?.let { body.put("description", it) }
        (args["public"] as? Boolean)?.let { body.put("public", it) }
        (args["collaborative"] as? Boolean)?.let { body.put("collaborative", it) }
        if (body.length() == 0) throw IllegalStateException("至少提供一个要更新的字段（name/description/public/collaborative）")
        api.put("/playlists/$playlistId", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "歌单信息已更新")
    }

    companion object {
        const val NAME: String = "spotify_update_playlist"
        const val DESCRIPTION: String = "更新歌单的名称、描述、公开/协作状态。"
    }
}
