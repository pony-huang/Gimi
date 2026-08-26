package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.toJsonNative
import org.json.JSONArray
import org.json.JSONObject

/** 个人库/播放列表/播放状态查询类工具。 */
internal fun libraryTools(api: SpotifyApi): List<BaseTool> = listOf(
    spotifyTool(
        name = "spotify_now_playing",
        description = "Show the currently playing track with progress, device, and volume info.",
        parameters = Schema(type = Type.OBJECT),
    ) {
        val json = api.get("/me/player/currently-playing")
        if (json == null) {
            mapOf(SpotifyTool.RESULT_KEY to "Nothing is currently playing")
        } else {
            mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
        }
    },
    spotifyTool(
        name = "spotify_get_my_playlists",
        description =
            "List playlists owned by or shared with the current user. " +
                "Use IDs from this tool with spotify_get_playlist or spotify_get_playlist_tracks.",
        parameters = objectSchema(*pagingParams()),
    ) { args ->
        val json = api.get(
            "/me/playlists",
            mapOf(
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
            ),
        )
        mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_playlist",
        description =
            "Get details for a playlist owned by or shared with the current user. " +
                "Only use playlist IDs returned by spotify_get_my_playlists; public chart playlist IDs may be inaccessible.",
        parameters = objectSchema(
            "playlist_id" to stringParam("Spotify playlist ID"),
            required = listOf("playlist_id"),
        ),
    ) { args ->
        val id = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val json = api.get("/playlists/$id") ?: throw IllegalStateException("Playlist not found")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_playlist_tracks",
        description =
            "List items in a playlist owned by or shared with the current user. " +
                "Only use playlist IDs returned by spotify_get_my_playlists, not public chart search results.",
        parameters = objectSchema(
            "playlist_id" to stringParam("Spotify playlist ID"),
            *pagingParams(),
            required = listOf("playlist_id"),
        ),
    ) { args ->
        val id = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
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
        mapOf(SpotifyTool.RESULT_KEY to projected.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_saved_tracks",
        description = "List the tracks saved in the user's Liked Songs library.",
        parameters = objectSchema(*pagingParams()),
    ) { args ->
        val json = api.get(
            "/me/tracks",
            mapOf(
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
            ),
        )
        mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_recently_played",
        description =
            "List the current user's recently played tracks with play times. " +
                "Use as a personal fallback when top tracks are unavailable; never describe these as global charts.",
        parameters = objectSchema(
            "limit" to intParam(
                "Number of results to return (default 50, max 50)",
                min = 0,
                max = 50,
            ),
        ),
    ) { args ->
        val json = api.get("/me/player/recently-played", mapOf("limit" to intArg(args, "limit", 50)))
        mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_queue",
        description = "Show the current playback queue (playing item + upcoming).",
        parameters = Schema(type = Type.OBJECT),
    ) {
        val json = api.get("/me/player/queue") ?: throw IllegalStateException("Failed to fetch the queue")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_devices",
        description = "List the user's Spotify Connect devices (name, type, active, volume).",
        parameters = Schema(type = Type.OBJECT),
    ) {
        val json = api.get("/me/player/devices") ?: throw IllegalStateException("Failed to fetch devices")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_top_tracks",
        description =
            "Preferred tool for requests such as popular songs, favorites, or personalized recommendations. " +
                "Returns the current user's most-played tracks, not global Spotify charts.",
        parameters = objectSchema(
            "time_range" to stringParam(
                "Time range: short_term (~4 weeks) / medium_term (~6 months) / long_term (~1 year). Default: medium_term",
                enum = listOf("short_term", "medium_term", "long_term"),
            ),
            *pagingParams(),
        ),
    ) { args ->
        val json = api.get(
            "/me/top/tracks",
            mapOf(
                "time_range" to (strArg(args, "time_range") ?: "medium_term"),
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
            ),
        )
        mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_top_artists",
        description = "Get the current user's most-played artists (listening statistics).",
        parameters = objectSchema(
            "time_range" to stringParam(
                "Time range: short_term (~4 weeks) / medium_term (~6 months) / long_term (~1 year). Default: medium_term",
                enum = listOf("short_term", "medium_term", "long_term"),
            ),
            *pagingParams(),
        ),
    ) { args ->
        val json = api.get(
            "/me/top/artists",
            mapOf(
                "time_range" to (strArg(args, "time_range") ?: "medium_term"),
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
            ),
        )
        mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    },
)

/** 分页 limit + offset 参数对（默认 50、最大 50）。 */
private fun pagingParams(
    limitDescription: String = "Number of results to return (default 50, max 50)",
    includeOffset: Boolean = true,
): Array<Pair<String, Schema>> = buildList {
    add("limit" to intParam(limitDescription, min = 0, max = 50))
    if (includeOffset) add("offset" to intParam("Pagination offset (default 0)", min = 0))
}.toTypedArray()
