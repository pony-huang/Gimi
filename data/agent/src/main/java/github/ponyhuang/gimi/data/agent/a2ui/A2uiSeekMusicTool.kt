package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.a2ui.A2uiRendererFunctions

/** 把当前会话的播放器跳转请求交给已挂载的渲染端函数。 */
internal class A2uiSeekMusicTool(private val renderer: A2uiRendererFunctions) : BaseTool(
    "seek_a2ui_music_player",
    "When the user requests a position change in an existing in-card music player, " +
        "seek that surface's mounted player to an absolute position.",
) {
    override fun declaration() = FunctionDeclaration(name, description, parameters = Schema(
        type = Type.OBJECT,
        properties = mapOf(
            "surface_id" to Schema(type = Type.STRING, description = "Existing music player's A2UI surface ID"),
            "position_ms" to Schema(type = Type.INTEGER, description = "Absolute playback position in milliseconds"),
        ),
        required = listOf("surface_id", "position_ms"),
    ))

    @OptIn(com.google.adk.kt.annotations.FrameworkInternalApi::class)
    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
        val sessionId = context.session.key.id
        val surfaceId = (args["surface_id"] as? String)?.takeIf(String::isNotBlank)
        val position = (args["position_ms"] as? Number)?.toDouble()
        if (sessionId == null || surfaceId == null || position == null ||
            position !in 0.0..Int.MAX_VALUE.toDouble() || position % 1.0 != 0.0
        ) return mapOf("error" to "Invalid surface or playback position")

        return if (renderer.seekMusicPlayer(sessionId, surfaceId, position.toLong())) {
            mapOf("success" to true, "position_ms" to position.toLong())
        } else {
            mapOf("error" to "The requested player or playback position is unavailable")
        }
    }
}
