package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.types.Schema
import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import org.json.JSONArray
import org.json.JSONObject

/** 播放控制类工具（均需 Spotify Premium）。 */
internal fun playbackTools(api: SpotifyApi): List<BaseTool> = listOf(
    spotifyTool(
        name = "spotify_play",
        description =
            "Start or resume Spotify playback. Track or episode URIs play as single items; " +
                "album/playlist/artist URIs play as a context. Requires Spotify Premium.",
        parameters = objectSchema(
            "uri" to stringParam(
                "Spotify URI to play (spotify:track:xxx / spotify:episode:xxx / spotify:album:xxx / " +
                    "spotify:playlist:xxx / spotify:artist:xxx); omit to resume current playback",
            ),
            "device_id" to stringParam("Target device ID; auto-selected if omitted"),
            "offset" to intParam(
                "Start position within an album/playlist context (0-based)",
                min = 0,
            ),
            "position_ms" to intParam("Start playback at this position in milliseconds", min = 0),
        ),
    ) { args ->
        val deviceId = api.ensureActiveDevice(strArg(args, "device_id"))
        val uri = strArg(args, "uri")
        val body = JSONObject()
        if (uri != null) {
            // 单曲/单集 → uris；专辑/歌单/艺人 → context_uri。
            if (uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:")) {
                body.put("uris", JSONArray().put(uri))
            } else {
                body.put("context_uri", uri)
            }
            intArg(args, "offset", 0).takeIf { it > 0 }?.let {
                body.put("offset", JSONObject().put("position", it))
            }
            intArg(args, "position_ms", 0).takeIf { it > 0 }?.let { body.put("position_ms", it) }
        }
        api.put("/me/player/play", mapOf("device_id" to deviceId), body)
        mapOf(SpotifyTool.RESULT_KEY to ("Playback started" + (uri?.let { ": $it" } ?: "")))
    },
    spotifyTool(
        name = "spotify_pause",
        description = "Pause current Spotify playback. Requires Premium.",
        parameters = objectSchema(
            "device_id" to stringParam("Target device ID; auto-selected if omitted"),
        ),
    ) { args ->
        api.put("/me/player/pause", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        mapOf(SpotifyTool.RESULT_KEY to "Playback paused")
    },
    spotifyTool(
        name = "spotify_next",
        description = "Skip to the next track. Requires Premium.",
        parameters = objectSchema(
            "device_id" to stringParam("Target device ID; auto-selected if omitted"),
        ),
    ) { args ->
        api.post("/me/player/next", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        mapOf(SpotifyTool.RESULT_KEY to "Skipped to next track")
    },
    spotifyTool(
        name = "spotify_previous",
        description = "Skip to the previous track. Requires Premium.",
        parameters = objectSchema(
            "device_id" to stringParam("Target device ID; auto-selected if omitted"),
        ),
    ) { args ->
        api.post("/me/player/previous", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        mapOf(SpotifyTool.RESULT_KEY to "Skipped to previous track")
    },
    spotifyTool(
        name = "spotify_set_volume",
        description = "Set the playback volume (0-100). Requires Premium.",
        parameters = objectSchema(
            "volume_percent" to intParam(
                "Volume percentage 0-100",
                min = 0,
                max = 100,
            ),
            "device_id" to stringParam("Target device ID; auto-selected if omitted"),
            required = listOf("volume_percent"),
        ),
    ) { args ->
        val volume = intArg(args, "volume_percent", -1).coerceIn(0, 100)
        api.put(
            "/me/player/volume",
            mapOf("volume_percent" to volume, "device_id" to (strArg(args, "device_id") ?: "")),
        )
        mapOf(SpotifyTool.RESULT_KEY to "Volume set to $volume%")
    },
    spotifyTool(
        name = "spotify_add_to_queue",
        description = "Add a track or episode to the current playback queue. Requires Premium.",
        parameters = objectSchema(
            "uri" to stringParam("Spotify URI to enqueue (spotify:track:xxx / spotify:episode:xxx)"),
            "device_id" to stringParam("Target device ID; auto-selected if omitted"),
            required = listOf("uri"),
        ),
    ) { args ->
        val uri = strArg(args, "uri") ?: throw IllegalStateException("Missing parameter uri")
        api.post("/me/player/queue", mapOf("uri" to uri, "device_id" to (strArg(args, "device_id") ?: "")))
        mapOf(SpotifyTool.RESULT_KEY to "Added to queue: $uri")
    },
)

