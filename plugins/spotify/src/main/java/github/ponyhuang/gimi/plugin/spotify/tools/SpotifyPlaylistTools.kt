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

/** spotify_create_playlist — create a playlist. */
private class SpotifyCreatePlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "name" to Schema(type = Type.STRING, description = "Playlist name, required"),
                "description" to Schema(type = Type.STRING, description = "Playlist description (optional)"),
                "public" to Schema(type = Type.BOOLEAN, description = "Whether the playlist is public (default false)"),
            ),
            required = listOf("name"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val name = strArg(args, "name") ?: throw IllegalStateException("Missing parameter name")
        val body = JSONObject()
            .put("name", name)
            .put("public", boolArg(args, "public", false))
        strArg(args, "description")?.let { body.put("description", it) }
        val json = api.post("/me/playlists", body = body) ?: throw IllegalStateException("Failed to create playlist")
        return mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    }

    companion object {
        const val NAME: String = "spotify_create_playlist"
        const val DESCRIPTION: String = "Create a new Spotify playlist."
    }
}

/** spotify_add_tracks_to_playlist — add tracks to a playlist. */
private class SpotifyAddTracksToPlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "Target playlist ID"),
                "track_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Track IDs or URIs to add",
                    items = Schema(type = Type.STRING),
                ),
                "position" to Schema(type = Type.INTEGER, description = "Insert position (0-based); omit to append"),
            ),
            required = listOf("playlist_id", "track_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val uris = listArg(args, "track_ids").map(::spotifyTrackUri)
        if (uris.isEmpty()) throw IllegalStateException("track_ids must not be empty")
        val body = JSONObject().put("uris", JSONArray().apply { uris.forEach(::put) })
        intArg(args, "position", -1).takeIf { it >= 0 }?.let { body.put("position", it) }
        api.post("/playlists/$playlistId/items", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "Added ${uris.size} track(s) to playlist")
    }

    companion object {
        const val NAME: String = "spotify_add_tracks_to_playlist"
        const val DESCRIPTION: String = "Add one or more tracks to a playlist."
    }
}

/** spotify_remove_tracks_from_playlist — remove tracks from a playlist. */
private class SpotifyRemoveTracksFromPlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "Target playlist ID"),
                "track_ids" to Schema(
                    type = Type.ARRAY,
                    description = "Track IDs or URIs to remove (max 100)",
                    items = Schema(type = Type.STRING),
                ),
                "snapshot_id" to Schema(type = Type.STRING, description = "Playlist snapshot ID (optional, for concurrency)"),
            ),
            required = listOf("playlist_id", "track_ids"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val uris = listArg(args, "track_ids").take(100).map(::spotifyTrackUri)
        if (uris.isEmpty()) throw IllegalStateException("track_ids must not be empty")
        val items = JSONArray().apply {
            uris.forEach { id -> put(JSONObject().put("uri", id)) }
        }
        val body = JSONObject().put("items", items)
        strArg(args, "snapshot_id")?.let { body.put("snapshot_id", it) }
        api.delete("/playlists/$playlistId/items", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "Removed ${uris.size} track(s) from playlist")
    }

    companion object {
        const val NAME: String = "spotify_remove_tracks_from_playlist"
        const val DESCRIPTION: String = "Remove one or more tracks from a playlist."
    }
}

/** spotify_reorder_playlist_items — reorder tracks within a playlist. */
private class SpotifyReorderPlaylistItemsTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "Target playlist ID"),
                "range_start" to Schema(type = Type.INTEGER, description = "Position of the first item to move (0-based)"),
                "insert_before" to Schema(type = Type.INTEGER, description = "Position to insert at (0-based)"),
                "range_length" to Schema(type = Type.INTEGER, description = "Number of consecutive items to move (default 1)"),
                "snapshot_id" to Schema(type = Type.STRING, description = "Playlist snapshot ID (optional)"),
            ),
            required = listOf("playlist_id", "range_start", "insert_before"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val body = JSONObject()
            .put("range_start", intArg(args, "range_start", 0))
            .put("insert_before", intArg(args, "insert_before", 0))
        intArg(args, "range_length", -1).takeIf { it >= 0 }?.let { body.put("range_length", it) }
        strArg(args, "snapshot_id")?.let { body.put("snapshot_id", it) }
        api.put("/playlists/$playlistId/items", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "Reordered playlist items")
    }

    companion object {
        const val NAME: String = "spotify_reorder_playlist_items"
        const val DESCRIPTION: String = "Reorder a range of tracks within a playlist."
    }
}

/** spotify_update_playlist — update playlist details. */
private class SpotifyUpdatePlaylistTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "playlist_id" to Schema(type = Type.STRING, description = "Target playlist ID"),
                "name" to Schema(type = Type.STRING, description = "New name (optional)"),
                "description" to Schema(type = Type.STRING, description = "New description (optional)"),
                "public" to Schema(type = Type.BOOLEAN, description = "Whether the playlist is public (optional)"),
                "collaborative" to Schema(type = Type.BOOLEAN, description = "Whether the playlist is collaborative (optional)"),
            ),
            required = listOf("playlist_id"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val body = JSONObject()
        strArg(args, "name")?.let { body.put("name", it) }
        (args["description"] as? String)?.let { body.put("description", it) }
        (args["public"] as? Boolean)?.let { body.put("public", it) }
        (args["collaborative"] as? Boolean)?.let { body.put("collaborative", it) }
        if (body.length() == 0) {
            throw IllegalStateException("Provide at least one field to update (name/description/public/collaborative)")
        }
        api.put("/playlists/$playlistId", body = body)
        return mapOf(SpotifyTool.RESULT_KEY to "Playlist updated")
    }

    companion object {
        const val NAME: String = "spotify_update_playlist"
        const val DESCRIPTION: String =
            "Update a playlist's name, description, or public/collaborative status."
    }
}
