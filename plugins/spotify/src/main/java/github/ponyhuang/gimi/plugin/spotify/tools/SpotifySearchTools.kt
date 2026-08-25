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

/** spotify_search — full-text search across tracks/albums/artists/playlists. */
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
                    description = "Item type to search: track / album / artist / playlist. Default: track",
                    enum = listOf("track", "album", "artist", "playlist"),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 10, max 50)"),
                "offset" to Schema(type = Type.INTEGER, description = "Pagination offset (default 0)"),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val query = strArg(args, "query") ?: throw IllegalStateException("Missing parameter query")
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
        const val DESCRIPTION: String =
            "Search tracks, albums, artists, or playlists on Spotify and return matching items (with IDs)."
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

/** spotify_get_tracks — batch track lookup. */
private class SpotifyGetTracksBatchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "track_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Spotify track IDs (max 50)",
                    items = Schema(type = Type.STRING),
                ),
            ),
            required = listOf("track_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val ids = listArg(args, "track_ids")
        if (ids.isEmpty()) throw IllegalStateException("Missing parameter track_ids")
        val json = api.get("/tracks", mapOf("ids" to ids.take(50).joinToString(","), "market" to "from_token"))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("tracks") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_tracks"
        const val DESCRIPTION: String = "Batch-fetch multiple tracks' details (up to 50 IDs)."
    }
}

/** spotify_get_albums — batch album lookup. */
private class SpotifyGetAlbumsBatchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "album_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Spotify album IDs (max 20)",
                    items = Schema(type = Type.STRING),
                ),
            ),
            required = listOf("album_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val ids = listArg(args, "album_ids")
        if (ids.isEmpty()) throw IllegalStateException("Missing parameter album_ids")
        val json = api.get("/albums", mapOf("ids" to ids.take(20).joinToString(","), "market" to "from_token"))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("albums") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_albums"
        const val DESCRIPTION: String = "Batch-fetch multiple albums' details (up to 20 IDs)."
    }
}

/** spotify_get_artists — batch artist lookup. */
private class SpotifyGetArtistsBatchTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "artist_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Spotify artist IDs (max 50)",
                    items = Schema(type = Type.STRING),
                ),
            ),
            required = listOf("artist_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val ids = listArg(args, "artist_ids")
        if (ids.isEmpty()) throw IllegalStateException("Missing parameter artist_ids")
        val json = api.get("/artists", mapOf("ids" to ids.take(50).joinToString(",")))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("artists") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_artists"
        const val DESCRIPTION: String = "Batch-fetch multiple artists' details (up to 50 IDs)."
    }
}

/** spotify_recommendations — recommendations based on seeds. */
private class SpotifyRecommendationsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "seed_tracks" to Schema(
                    type = Type.ARRAY,
                    description = "Seed track IDs (max 5; provide at least one of tracks/artists/genres)",
                    items = Schema(type = Type.STRING),
                ),
                "seed_artists" to Schema(
                    type = Type.ARRAY,
                    description = "Seed artist IDs (max 5)",
                    items = Schema(type = Type.STRING),
                ),
                "seed_genres" to Schema(
                    type = Type.ARRAY,
                    description = "Seed genres (max 5, e.g. pop / rock / electronic)",
                    items = Schema(type = Type.STRING),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 10, max 50)"),
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
        const val DESCRIPTION: String =
            "Generate Spotify recommendations based on seed tracks, artists, or genres."
    }
}
