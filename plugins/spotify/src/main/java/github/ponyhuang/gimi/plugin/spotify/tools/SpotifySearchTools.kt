package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.toJsonNative
import org.json.JSONArray

internal fun searchTools(api: SpotifyApi): List<BaseTool> = listOf(
    SpotifySearchTool(api),
    SpotifyGetTrackTool(api),
    SpotifyGetAlbumTool(api),
    SpotifyGetArtistTool(api),
    SpotifyGetAlbumTracksTool(api),
    SpotifyGetTracksBatchTool(api),
    SpotifyGetAlbumsBatchTool(api),
    SpotifyGetArtistsBatchTool(api),
    SpotifyRecommendationsTool(api),
)

/** spotify_search — 全站搜索（歌曲/专辑/歌手/歌单）。 */
private class SpotifySearchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "搜索关键词，不能为空"),
                "type" to Schema(
                    type = Type.STRING,
                    description = "搜索类型：track / album / artist / playlist，默认 track",
                    enum = listOf("track", "album", "artist", "playlist"),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，最大 50"),
                "offset" to Schema(type = Type.INTEGER, description = "分页偏移，默认 0"),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val query = strArg(args, "query") ?: throw IllegalStateException("缺少参数 query")
        val type = strArg(args, "type") ?: "track"
        val json = api.get(
            "/search",
            mapOf(
                "q" to query,
                "type" to type,
                "limit" to intArg(args, "limit", 10),
                "offset" to intArg(args, "offset", 0),
                "market" to "from_token",
            ),
        )
        val items = json?.optJSONObject(type)?.optJSONArray("items") ?: JSONArray()
        return mapOf(SpotifyTool.RESULT_KEY to items.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_search"
        const val DESCRIPTION: String = "在 Spotify 搜索歌曲、专辑、歌手或歌单，返回匹配项列表（含 ID）。"
    }
}

/** spotify_get_track — 单曲详情。 */
private class SpotifyGetTrackTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("track_id" to Schema(type = Type.STRING, description = "Spotify 曲目 ID")),
            required = listOf("track_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "track_id") ?: throw IllegalStateException("缺少参数 track_id")
        val json = api.get("/tracks/$id", mapOf("market" to "from_token"))
            ?: throw IllegalStateException("未找到该曲目")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_track"
        const val DESCRIPTION: String = "获取单首歌曲详情（标题、歌手、专辑、时长、链接等）。"
    }
}

/** spotify_get_album — 专辑详情。 */
private class SpotifyGetAlbumTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("album_id" to Schema(type = Type.STRING, description = "Spotify 专辑 ID")),
            required = listOf("album_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "album_id") ?: throw IllegalStateException("缺少参数 album_id")
        val json = api.get("/albums/$id", mapOf("market" to "from_token"))
            ?: throw IllegalStateException("未找到该专辑")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_album"
        const val DESCRIPTION: String = "获取专辑详情（名称、歌手、发行日期、类型等）。"
    }
}

/** spotify_get_artist — 歌手详情。 */
private class SpotifyGetArtistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("artist_id" to Schema(type = Type.STRING, description = "Spotify 歌手 ID")),
            required = listOf("artist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "artist_id") ?: throw IllegalStateException("缺少参数 artist_id")
        val json = api.get("/artists/$id")
            ?: throw IllegalStateException("未找到该歌手")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_artist"
        const val DESCRIPTION: String = "获取歌手详情（名称、流派、粉丝数等）。"
    }
}

/** spotify_get_album_tracks — 专辑曲目列表。 */
private class SpotifyGetAlbumTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "album_id" to Schema(type = Type.STRING, description = "Spotify 专辑 ID"),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50"),
                "offset" to Schema(type = Type.INTEGER, description = "分页偏移，默认 0"),
            ),
            required = listOf("album_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "album_id") ?: throw IllegalStateException("缺少参数 album_id")
        val json = api.get(
            "/albums/$id/tracks",
            mapOf(
                "limit" to intArg(args, "limit", 20),
                "offset" to intArg(args, "offset", 0),
                "market" to "from_token",
            ),
        )
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_album_tracks"
        const val DESCRIPTION: String = "获取一张专辑内的曲目列表。"
    }
}

/** spotify_get_tracks — 批量查询多首歌曲。 */
private class SpotifyGetTracksBatchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "track_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Spotify 曲目 ID 列表（最多 50 个）",
                    items = Schema(type = Type.STRING),
                ),
            ),
            required = listOf("track_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val ids = listArg(args, "track_ids")
        if (ids.isEmpty()) throw IllegalStateException("缺少参数 track_ids")
        val json = api.get("/tracks", mapOf("ids" to ids.take(50).joinToString(","), "market" to "from_token"))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("tracks") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_tracks"
        const val DESCRIPTION: String = "批量获取多首歌曲详情（最多 50 个 ID）。"
    }
}

/** spotify_get_albums — 批量查询多张专辑。 */
private class SpotifyGetAlbumsBatchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "album_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Spotify 专辑 ID 列表（最多 20 个）",
                    items = Schema(type = Type.STRING),
                ),
            ),
            required = listOf("album_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val ids = listArg(args, "album_ids")
        if (ids.isEmpty()) throw IllegalStateException("缺少参数 album_ids")
        val json = api.get("/albums", mapOf("ids" to ids.take(20).joinToString(","), "market" to "from_token"))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("albums") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_albums"
        const val DESCRIPTION: String = "批量获取多张专辑详情（最多 20 个 ID）。"
    }
}

/** spotify_get_artists — 批量查询多位歌手。 */
private class SpotifyGetArtistsBatchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "artist_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Spotify 歌手 ID 列表（最多 50 个）",
                    items = Schema(type = Type.STRING),
                ),
            ),
            required = listOf("artist_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val ids = listArg(args, "artist_ids")
        if (ids.isEmpty()) throw IllegalStateException("缺少参数 artist_ids")
        val json = api.get("/artists", mapOf("ids" to ids.take(50).joinToString(",")))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("artists") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_artists"
        const val DESCRIPTION: String = "批量获取多位歌手详情（最多 50 个 ID）。"
    }
}

/** spotify_recommendations — 基于种子推荐歌曲。 */
private class SpotifyRecommendationsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "seed_tracks" to Schema(
                    type = Type.ARRAY,
                    description = "种子曲目 ID（与歌手/流派至少提供一个，最多 5 个）",
                    items = Schema(type = Type.STRING),
                ),
                "seed_artists" to Schema(
                    type = Type.ARRAY,
                    description = "种子歌手 ID（最多 5 个）",
                    items = Schema(type = Type.STRING),
                ),
                "seed_genres" to Schema(
                    type = Type.ARRAY,
                    description = "种子流派（最多 5 个，如 pop / rock / electronic）",
                    items = Schema(type = Type.STRING),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，最大 50"),
            ),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val query = mutableMapOf<String, Any?>("limit" to intArg(args, "limit", 10), "market" to "from_token")
        listArg(args, "seed_tracks").take(5).takeIf { it.isNotEmpty() }
            ?.let { query["seed_tracks"] = it.joinToString(",") }
        listArg(args, "seed_artists").take(5).takeIf { it.isNotEmpty() }
            ?.let { query["seed_artists"] = it.joinToString(",") }
        listArg(args, "seed_genres").take(5).takeIf { it.isNotEmpty() }
            ?.let { query["seed_genres"] = it.joinToString(",") }
        val json = api.get("/recommendations", query)
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("tracks") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_recommendations"
        const val DESCRIPTION: String = "根据种子歌曲/歌手/流派生成 Spotify 推荐歌曲列表。"
    }
}
