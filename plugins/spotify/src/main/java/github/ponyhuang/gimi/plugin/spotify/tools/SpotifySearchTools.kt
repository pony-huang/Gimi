package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.toJsonNative
import org.json.JSONArray

/**
 * 官方 /search 的 type 支持 7 类，但本插件只开放能直接进入播放/收藏流程的
 * track/album/artist；不开放 playlist，避免搜索到用户无权访问的公开歌单（403 死胡同）。
 */
private val SEARCH_TYPES: List<String> = listOf("track", "album", "artist")

internal fun searchTools(api: SpotifyApi): List<BaseTool> = listOf(
    spotifyTool(
        name = "spotify_search",
        description =
            "Search Spotify for a concrete song, album, or artist name and return matching items with IDs. " +
                "Do not use for charts, popularity, recommendations, or listening history; " +
                "use spotify_get_top_tracks for those requests.",
        parameters = objectSchema(
            "query" to stringParam("Search term, required"),
            "type" to stringParam(
                "Item type to search. Default: track",
                enum = SEARCH_TYPES,
            ),
            "limit" to intParam(
                "Number of results to return (default 50, max 50)",
                min = 0,
                max = 50,
            ),
            "offset" to intParam(
                "Pagination offset (default 0, max 1000)",
                min = 0,
                max = 1000,
            ),
            required = listOf("query"),
        ),
    ) { args ->
        val query = strArg(args, "query") ?: throw IllegalStateException("Missing parameter query")
        val type = strArg(args, "type")?.takeIf { it in SEARCH_TYPES } ?: "track"
        val json = api.get(
            "/search",
            mapOf(
                "q" to query,
                "type" to type,
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
                "market" to "from_token",
            ),
        )
        val items = json?.optJSONObject(type)?.optJSONArray("items") ?: JSONArray()
        if (items.length() == 0) {
            mapOf(
                SpotifyTool.RESULT_KEY to emptyList<Any>(),
                "guidance" to "No matches. Do not repeat broad chart or popularity searches; " +
                    "use spotify_get_top_tracks for the user's popular music, or ask for a concrete song or artist.",
            )
        } else {
            mapOf(SpotifyTool.RESULT_KEY to items.toJsonNative())
        }
    },
    spotifyTool(
        name = "spotify_get_track",
        description = "Get a single track's details (title, artists, album, duration, URL, etc.).",
        parameters = objectSchema(
            "track_id" to stringParam("Spotify track ID"),
            required = listOf("track_id"),
        ),
    ) { args ->
        val id = strArg(args, "track_id") ?: throw IllegalStateException("Missing parameter track_id")
        val json = api.get("/tracks/$id", mapOf("market" to "from_token"))
            ?: throw IllegalStateException("Track not found")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_album",
        description = "Get an album's details (name, artists, release date, type, etc.).",
        parameters = objectSchema(
            "album_id" to stringParam("Spotify album ID"),
            required = listOf("album_id"),
        ),
    ) { args ->
        val id = strArg(args, "album_id") ?: throw IllegalStateException("Missing parameter album_id")
        val json = api.get("/albums/$id", mapOf("market" to "from_token"))
            ?: throw IllegalStateException("Album not found")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_artist",
        description = "Get an artist's details (name, genres, follower count, etc.).",
        parameters = objectSchema(
            "artist_id" to stringParam("Spotify artist ID"),
            required = listOf("artist_id"),
        ),
    ) { args ->
        val id = strArg(args, "artist_id") ?: throw IllegalStateException("Missing parameter artist_id")
        val json = api.get("/artists/$id") ?: throw IllegalStateException("Artist not found")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_get_album_tracks",
        description = "List the tracks of a single album.",
        parameters = objectSchema(
            "album_id" to stringParam("Spotify album ID"),
            "limit" to intParam(
                "Number of results to return (default 50, max 50)",
                min = 0,
                max = 50,
            ),
            "offset" to intParam("Pagination offset (default 0)", min = 0),
            required = listOf("album_id"),
        ),
    ) { args ->
        val id = strArg(args, "album_id") ?: throw IllegalStateException("Missing parameter album_id")
        val json = api.get(
            "/albums/$id/tracks",
            mapOf(
                "limit" to intArg(args, "limit", 50),
                "offset" to intArg(args, "offset", 0),
                "market" to "from_token",
            ),
        )
        mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    },
)
