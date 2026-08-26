package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.toJsonNative
import org.json.JSONArray
import org.json.JSONObject

/** 歌单编辑类工具。 */
internal fun playlistTools(api: SpotifyApi): List<BaseTool> = listOf(
    spotifyTool(
        name = "spotify_create_playlist",
        description = "Create a new Spotify playlist.",
        parameters = objectSchema(
            "name" to stringParam("Playlist name"),
            "description" to stringParam("Playlist description (optional)"),
            "public" to Schema(
                type = Type.BOOLEAN,
                description = "Whether the playlist is public (default false)",
            ),
            "collaborative" to Schema(
                type = Type.BOOLEAN,
                description = "Whether the playlist is collaborative (default false)",
            ),
            required = listOf("name"),
        ),
    ) { args ->
        val name = strArg(args, "name") ?: throw IllegalStateException("Missing parameter name")
        val body = JSONObject()
            .put("name", name)
            .put("public", boolArg(args, "public", false))
            .put("collaborative", boolArg(args, "collaborative", false))
        strArg(args, "description")?.let { body.put("description", it) }
        val json = api.post("/me/playlists", body = body) ?: throw IllegalStateException("Failed to create playlist")
        mapOf(SpotifyTool.RESULT_KEY to json.toJsonNative())
    },
    spotifyTool(
        name = "spotify_add_tracks_to_playlist",
        description = "Add one or more tracks to a playlist.",
        parameters = objectSchema(
            "playlist_id" to stringParam("Target playlist ID"),
            "track_ids" to stringListParam(
                "Track IDs or URIs to add (max 100)",
                maxItems = 100,
            ),
            "position" to intParam(
                "Insert position (0-based); omit to append",
                min = 0,
            ),
            required = listOf("playlist_id", "track_ids"),
        ),
    ) { args ->
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val uris = listArg(args, "track_ids").take(100).map(::spotifyTrackUri)
        if (uris.isEmpty()) throw IllegalStateException("track_ids must not be empty")
        val body = JSONObject().put("uris", JSONArray().apply { uris.forEach(::put) })
        intArg(args, "position", -1).takeIf { it >= 0 }?.let { body.put("position", it) }
        api.post("/playlists/$playlistId/items", body = body)
        mapOf(SpotifyTool.RESULT_KEY to "Added ${uris.size} track(s) to playlist")
    },
    spotifyTool(
        name = "spotify_remove_tracks_from_playlist",
        description = "Remove one or more tracks from a playlist.",
        parameters = objectSchema(
            "playlist_id" to stringParam("Target playlist ID"),
            "track_ids" to stringListParam(
                "Track IDs or URIs to remove (max 100)",
                maxItems = 100,
            ),
            "snapshot_id" to stringParam("Playlist snapshot ID (optional, for concurrency)"),
            required = listOf("playlist_id", "track_ids"),
        ),
    ) { args ->
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val uris = listArg(args, "track_ids").take(100).map(::spotifyTrackUri)
        if (uris.isEmpty()) throw IllegalStateException("track_ids must not be empty")
        val items = JSONArray().apply {
            uris.forEach { id -> put(JSONObject().put("uri", id)) }
        }
        val body = JSONObject().put("items", items)
        strArg(args, "snapshot_id")?.let { body.put("snapshot_id", it) }
        api.delete("/playlists/$playlistId/items", body = body)
        mapOf(SpotifyTool.RESULT_KEY to "Removed ${uris.size} track(s) from playlist")
    },
    spotifyTool(
        name = "spotify_reorder_playlist_items",
        description = "Reorder a range of tracks within a playlist.",
        parameters = objectSchema(
            "playlist_id" to stringParam("Target playlist ID"),
            "range_start" to intParam("Position of the first item to move (0-based)", min = 0),
            "insert_before" to intParam("Position to insert at (0-based)", min = 0),
            "range_length" to intParam("Number of consecutive items to move (default 1)", min = 1),
            "snapshot_id" to stringParam("Playlist snapshot ID (optional)"),
            required = listOf("playlist_id", "range_start", "insert_before"),
        ),
    ) { args ->
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val body = JSONObject()
            .put("range_start", intArg(args, "range_start", 0))
            .put("insert_before", intArg(args, "insert_before", 0))
        intArg(args, "range_length", -1).takeIf { it >= 0 }?.let { body.put("range_length", it) }
        strArg(args, "snapshot_id")?.let { body.put("snapshot_id", it) }
        api.put("/playlists/$playlistId/items", body = body)
        mapOf(SpotifyTool.RESULT_KEY to "Reordered playlist items")
    },
    spotifyTool(
        name = "spotify_update_playlist",
        description = "Update a playlist's name, description, or public/collaborative status.",
        parameters = objectSchema(
            "playlist_id" to stringParam("Target playlist ID"),
            "name" to stringParam("New name (optional)"),
            "description" to stringParam("New description (optional)"),
            "public" to Schema(type = Type.BOOLEAN, description = "Whether the playlist is public (optional)"),
            "collaborative" to Schema(type = Type.BOOLEAN, description = "Whether the playlist is collaborative (optional)"),
            required = listOf("playlist_id"),
        ),
    ) { args ->
        val playlistId = strArg(args, "playlist_id") ?: throw IllegalStateException("Missing parameter playlist_id")
        val body = JSONObject()
        strArg(args, "name")?.let { body.put("name", it) }
        strArg(args, "description")?.let { body.put("description", it) }
        (args["public"] as? Boolean)?.let { body.put("public", it) }
        (args["collaborative"] as? Boolean)?.let { body.put("collaborative", it) }
        if (body.length() == 0) {
            throw IllegalStateException("Provide at least one field to update (name/description/public/collaborative)")
        }
        api.put("/playlists/$playlistId", body = body)
        mapOf(SpotifyTool.RESULT_KEY to "Playlist updated")
    },
)

/** 普通 ID 统一成 spotify:track:{id} URI；已是 URI 则原样返回。 */
private fun spotifyTrackUri(id: String): String =
    if (id.startsWith("spotify:")) id else "spotify:track:$id"

/** string 数组参数 schema。 */
private fun stringListParam(description: String, maxItems: Int): Schema = Schema(
    type = Type.ARRAY,
    description = description,
    items = Schema(type = Type.STRING),
    minItems = 1,
    maxItems = maxItems.toLong(),
)
