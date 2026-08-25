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

/** spotify_play — 开始播放（歌曲/专辑/歌单/歌手）。需 Spotify Premium。 */
private class SpotifyPlayTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "uri" to Schema(
                    type = Type.STRING,
                    description = "要播放的 Spotify URI（spotify:track:xxx / spotify:album:xxx / spotify:playlist:xxx / spotify:artist:xxx）；省略则恢复当前播放",
                ),
                "device_id" to Schema(type = Type.STRING, description = "目标设备 ID，缺省自动选择可用设备"),
                "offset" to Schema(type = Type.INTEGER, description = "专辑/歌单内从第几首开始播放（0 起）"),
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
        return mapOf(SpotifyTool.RESULT_KEY to ("已开始播放" + (uri?.let { ": $it" } ?: "")))
    }

    companion object {
        const val NAME: String = "spotify_play"
        const val DESCRIPTION: String = "开始播放 Spotify 内容（歌曲/专辑/歌单/歌手），需 Spotify Premium。"
    }
}

/** spotify_pause — 暂停播放。 */
private class SpotifyPauseTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("device_id" to Schema(type = Type.STRING, description = "目标设备 ID，缺省自动")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        api.put("/me/player/pause", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "已暂停播放")
    }

    companion object {
        const val NAME: String = "spotify_pause"
        const val DESCRIPTION: String = "暂停当前 Spotify 播放（需 Premium）。"
    }
}

/** spotify_next — 切到下一首。 */
private class SpotifyNextTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("device_id" to Schema(type = Type.STRING, description = "目标设备 ID，缺省自动")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        api.post("/me/player/next", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "已切到下一首")
    }

    companion object {
        const val NAME: String = "spotify_next"
        const val DESCRIPTION: String = "跳到下一首（需 Premium）。"
    }
}

/** spotify_previous — 切回上一首。 */
private class SpotifyPreviousTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("device_id" to Schema(type = Type.STRING, description = "目标设备 ID，缺省自动")),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        api.post("/me/player/previous", mapOf("device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "已切到上一首")
    }

    companion object {
        const val NAME: String = "spotify_previous"
        const val DESCRIPTION: String = "跳到上一首（需 Premium）。"
    }
}

/** spotify_set_volume — 设置音量。 */
private class SpotifySetVolumeTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "volume_percent" to Schema(type = Type.INTEGER, description = "音量百分比 0-100"),
                "device_id" to Schema(type = Type.STRING, description = "目标设备 ID，缺省自动"),
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
        return mapOf(SpotifyTool.RESULT_KEY to "音量已设为 $volume%")
    }

    companion object {
        const val NAME: String = "spotify_set_volume"
        const val DESCRIPTION: String = "设置播放音量（0-100，需 Premium）。"
    }
}

/** spotify_add_to_queue — 加入播放队列。 */
private class SpotifyAddToQueueTool(private val api: SpotifyApi) : SpotifyTool(NAME, DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "uri" to Schema(type = Type.STRING, description = "要加入队列的 Spotify URI（spotify:track:xxx 等），不能为空"),
                "device_id" to Schema(type = Type.STRING, description = "目标设备 ID，缺省自动"),
            ),
            required = listOf("uri"),
        ),
    )

    override suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?> {
        val uri = strArg(args, "uri") ?: throw IllegalStateException("缺少参数 uri")
        api.post("/me/player/queue", mapOf("uri" to uri, "device_id" to (strArg(args, "device_id") ?: "")))
        return mapOf(SpotifyTool.RESULT_KEY to "已加入队列: $uri")
    }

    companion object {
        const val NAME: String = "spotify_add_to_queue"
        const val DESCRIPTION: String = "把歌曲加入当前播放队列（需 Premium）。"
    }
}
