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
)

/** spotify_search — 针对具体曲名、专辑或艺人关键词的全文搜索。 */
private class SpotifySearchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "Search term, required"),
                "type" to Schema(
                    type = Type.STRING,
                    description = "Item type to search: track / album / artist. Default: track",
                    enum = SUPPORTED_TYPES,
                ),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 10, max 50)"),
                "offset" to Schema(type = Type.INTEGER, description = "Pagination offset (default 0)"),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val query = strArg(args, "query") ?: throw IllegalStateException("Missing parameter query")
        val type = strArg(args, "type")?.takeIf { it in SUPPORTED_TYPES } ?: "track"
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
        return if (items.length() == 0) {
            mapOf(
                SpotifyTool.RESULT_KEY to emptyList<Any>(),
                "guidance" to "No matches. Do not repeat broad chart or popularity searches; " +
                    "use spotify_get_top_tracks for the user's popular music, or ask for a concrete song or artist.",
            )
        } else {
            mapOf(SpotifyTool.RESULT_KEY to items.toJsonNative())
        }
    }

    companion object {
        const val NAME: String = "spotify_search"
        const val DESCRIPTION: String =
            "Search Spotify for a concrete song, album, or artist name and return matching items with IDs. " +
                "Do not use for charts, popularity, recommendations, or listening history; " +
                "use spotify_get_top_tracks or spotify_get_recently_played for those requests."
        val SUPPORTED_TYPES: List<String> = listOf("track", "album", "artist")
    }
}

/** spotify_get_track — single track details. */
private class SpotifyGetTrackTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("track_id" to Schema(type = Type.STRING, description = "Spotify track ID")),
            required = listOf("track_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "track_id") ?: throw IllegalStateException("Missing parameter track_id")
        val json = api.get("/tracks/$id", mapOf("market" to "from_token"))
            ?: throw IllegalStateException("Track not found")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_track"
        const val DESCRIPTION: String =
            "Get a single track's details (title, artists, album, duration, URL, etc.)."
    }
}

/** spotify_get_album — album details. */
private class SpotifyGetAlbumTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("album_id" to Schema(type = Type.STRING, description = "Spotify album ID")),
            required = listOf("album_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "album_id") ?: throw IllegalStateException("Missing parameter album_id")
        val json = api.get("/albums/$id", mapOf("market" to "from_token"))
            ?: throw IllegalStateException("Album not found")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_album"
        const val DESCRIPTION: String =
            "Get an album's details (name, artists, release date, type, etc.)."
    }
}

/** spotify_get_artist — artist details. */
private class SpotifyGetArtistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("artist_id" to Schema(type = Type.STRING, description = "Spotify artist ID")),
            required = listOf("artist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "artist_id") ?: throw IllegalStateException("Missing parameter artist_id")
        val json = api.get("/artists/$id") ?: throw IllegalStateException("Artist not found")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_artist"
        const val DESCRIPTION: String =
            "Get an artist's details (name, genres, follower count, etc.)."
    }
}

/** spotify_get_album_tracks — tracks within an album. */
private class SpotifyGetAlbumTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "album_id" to Schema(type = Type.STRING, description = "Spotify album ID"),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 20, max 50)"),
                "offset" to Schema(type = Type.INTEGER, description = "Pagination offset (default 0)"),
            ),
            required = listOf("album_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "album_id") ?: throw IllegalStateException("Missing parameter album_id")
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
        const val DESCRIPTION: String = "List the tracks of a single album."
    }
}
