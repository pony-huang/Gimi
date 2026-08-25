package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import org.json.JSONArray
import org.json.JSONObject

internal fun playbackTools(api: SpotifyApi): List<BaseTool> = listOf(
    SpotifyPlayTool(api),
    SpotifyPauseTool(api),
    SpotifyNextTool(api),
    SpotifyPreviousTool(api),
    SpotifySetVolumeTool(api),
    SpotifyAddToQueueTool(api),
)

/** spotify_play — start playback. Requires Spotify Premium. */
private class SpotifyPlayTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "uri" to Schema(
                    type = Type.STRING,
                    description = "Spotify URI to play (spotify:track:xxx / spotify:album:xxx / spotify:playlist:xxx / spotify:artist:xxx); omit to resume current playback",
                ),
                "device_id" to Schema(type = Type.STRING, description = "Target device ID; auto-selected if omitted"),
                "offset" to Schema(type = Type.INTEGER, description = "Start position within an album/playlist (0-based)"),
            ),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val deviceId = api.ensureActiveDevice(strArg(args, "device_id"))
        val uri = strArg(args, "uri")
        val offset = intArg(args, "offset", 0)
        val body = JSONObject()
        if (uri != null) {
            if (uri.startsWith("spotify:track:")) {
                body.put("uris", JSONArray().put(uri))
            } else {
                body.put("context_uri", uri)
                if (offset > 0) body.put("offset", JSONObject().put("position", offset))
            }
        }
        api.put("/me/player/play", mapOf("device_id" to deviceId), body)
        return mapOf(SpotifyTool.RESULT_KEY to ("Playback started" + (uri?.let { ": $it" } ?: "")))
    }

    companion object {
        const val NAME: String = "spotify_play"
        const val DESCRIPTION: String =
            "Start playing Spotify content (track/album/playlist/artist). Requires Spotify Premium."
    }
}

/** spotify_pause — pause playback. */
private class SpotifyPauseTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("device_id" to Schema(type = Type.STRING, description = "Target device ID; auto-selected if omitted")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        api.put("/me/player/pause", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "Playback paused")
    }

    companion object {
        const val NAME: String = "spotify_pause"
        const val DESCRIPTION: String = "Pause current Spotify playback. Requires Premium."
    }
}

/** spotify_next — skip to the next track. */
private class SpotifyNextTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("device_id" to Schema(type = Type.STRING, description = "Target device ID; auto-selected if omitted")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        api.post("/me/player/next", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "Skipped to next track")
    }

    companion object {
        const val NAME: String = "spotify_next"
        const val DESCRIPTION: String = "Skip to the next track. Requires Premium."
    }
}

/** spotify_previous — skip back to the previous track. */
private class SpotifyPreviousTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("device_id" to Schema(type = Type.STRING, description = "Target device ID; auto-selected if omitted")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        api.post("/me/player/previous", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "Skipped to previous track")
    }

    companion object {
        const val NAME: String = "spotify_previous"
        const val DESCRIPTION: String = "Skip to the previous track. Requires Premium."
    }
}

/** spotify_set_volume — set playback volume. */
private class SpotifySetVolumeTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "volume_percent" to Schema(type = Type.INTEGER, description = "Volume percentage 0-100"),
                "device_id" to Schema(type = Type.STRING, description = "Target device ID; auto-selected if omitted"),
            ),
            required = listOf("volume_percent"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val volume = intArg(args, "volume_percent", -1).coerceIn(0, 100)
        api.put(
            "/me/player/volume",
            mapOf("volume_percent" to volume, "device_id" to (strArg(args, "device_id") ?: "")),
        )
        return mapOf(SpotifyTool.RESULT_KEY to "Volume set to $volume%")
    }

    companion object {
        const val NAME: String = "spotify_set_volume"
        const val DESCRIPTION: String = "Set the playback volume (0-100). Requires Premium."
    }
}

/** spotify_add_to_queue — add an item to the playback queue. */
private class SpotifyAddToQueueTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "uri" to Schema(type = Type.STRING, description = "Spotify URI to enqueue (spotify:track:xxx etc.), required"),
                "device_id" to Schema(type = Type.STRING, description = "Target device ID; auto-selected if omitted"),
            ),
            required = listOf("uri"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val uri = strArg(args, "uri") ?: throw IllegalStateException("Missing parameter uri")
        api.post("/me/player/queue", mapOf("uri" to uri, "device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "Added to queue: $uri")
    }

    companion object {
        const val NAME: String = "spotify_add_to_queue"
        const val DESCRIPTION: String = "Add a track to the current playback queue. Requires Premium."
    }
}
