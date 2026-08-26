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

/** spotify_now_playing — currently playing content. */
private class SpotifyNowPlayingTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/currently-playing")
        return if (json == null) {
            mapOf(SpotifyTool.RESULT_KEY to "Nothing is currently playing")
        } else {
            mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
        }
    }

    companion object {
        const val NAME: String = "spotify_now_playing"
        const val DESCRIPTION: String =
            "Show the currently playing track with progress, device, and volume info."
    }
}

/** spotify_get_my_playlists — the user's playlists. */
private class SpotifyMyPlaylistsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 20, max 50)"),
                "offset" to Schema(type = Type.INTEGER, description = "Pagination offset (default 0)"),
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
        const val DESCRIPTION: String =
            "List playlists owned by or shared with the current user. " +
                "Use IDs from this tool with spotify_get_playlist or spotify_get_playlist_tracks."
    }
}

/** spotify_get_playlist — single playlist details. */
private class SpotifyGetPlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("playlist_id" to Schema(type = Type.STRING, description = "Spotify playlist ID")),
            required = listOf("playlist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val id = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val json = api.get("/playlists/$id") ?: throw IllegalStateException("Playlist not found")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_playlist"
        const val DESCRIPTION: String =
            "Get details for a playlist owned by or shared with the current user. " +
                "Only use playlist IDs returned by spotify_get_my_playlists; public chart playlist IDs may be inaccessible."
    }
}

/** spotify_get_playlist_tracks — tracks in a playlist (post-2026 /items endpoint). */
private class SpotifyGetPlaylistTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "Spotify playlist ID"),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 50, max 50)"),
                "offset" to Schema(type = Type.INTEGER, description = "Pagination offset (default 0)"),
            ),
            required = listOf("playlist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
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
        return mapOf(SpotifyTool.RESULT_KEY to projected.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_playlist_tracks"
        const val DESCRIPTION: String =
            "List items in a playlist owned by or shared with the current user. " +
                "Only use playlist IDs returned by spotify_get_my_playlists, not public chart search results."
    }
}

/** spotify_get_saved_tracks — the user's Liked Songs. */
private class SpotifySavedTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 20, max 50)"),
                "offset" to Schema(type = Type.INTEGER, description = "Pagination offset (default 0)"),
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
        const val DESCRIPTION: String = "List the tracks saved in the user's Liked Songs library."
    }
}

/** spotify_get_recently_played — recent playback history. */
private class SpotifyRecentlyPlayedTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 20, max 50)")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/recently-played", mapOf("limit" to intArg(args, "limit", 20)))
        return mapOf(SpotifyTool.RESULT_KEY to (json?.optJSONArray("items") ?: JSONArray()).toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_recently_played"
        const val DESCRIPTION: String =
            "List the current user's recently played tracks with play times. " +
                "Use as a personal fallback when top tracks are unavailable; never describe these as global charts."
    }
}

/** spotify_get_queue — the current playback queue. */
private class SpotifyQueueTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/queue") ?: throw IllegalStateException("Failed to fetch the queue")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_queue"
        const val DESCRIPTION: String = "Show the current playback queue (playing item + upcoming)."
    }
}

/** spotify_get_devices — available Spotify Connect devices. */
private class SpotifyDevicesTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val json = api.get("/me/player/devices") ?: throw IllegalStateException("Failed to fetch devices")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_get_devices"
        const val DESCRIPTION: String =
            "List the user's Spotify Connect devices (name, type, active, volume)."
    }
}

/** spotify_get_top_tracks — the user's most-played tracks. */
private class SpotifyTopTracksTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "time_range" to Schema(
                    type = Type.STRING,
                    description = "Time range: short_term (~4 weeks) / medium_term (~6 months) / long_term (~1 year). Default: medium_term",
                    enum = listOf("short_term", "medium_term", "long_term"),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 20, max 50)"),
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
        const val DESCRIPTION: String =
            "Preferred tool for requests such as popular songs, favorites, or personalized recommendations. " +
                "Returns the current user's most-played tracks, not global Spotify charts."
    }
}

/** spotify_get_top_artists — the user's most-played artists. */
private class SpotifyTopArtistsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "time_range" to Schema(
                    type = Type.STRING,
                    description = "Time range: short_term (~4 weeks) / medium_term (~6 months) / long_term (~1 year). Default: medium_term",
                    enum = listOf("short_term", "medium_term", "long_term"),
                ),
                "limit" to Schema(type = Type.INTEGER, description = "Number of results to return (default 20, max 50)"),
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
        const val DESCRIPTION: String = "Get the current user's most-played artists (listening statistics)."
    }
}
