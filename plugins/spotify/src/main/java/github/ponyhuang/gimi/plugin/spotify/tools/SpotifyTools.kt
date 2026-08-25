package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spotify 工具基类 — 统一 IO 调度与错误包装。
 *
 * 子类实现 [executeSafe]；抛出的异常经 [spotifyCall] 包装为 `{error: msg}`，
 * 返回值为 JSON-native（Map/List/String/Number/Boolean/null）。
 */
internal abstract class SpotifyTool(
    name: String,
    description: String,
) : FunctionTool(name = name, description = description) {

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        spotifyCall { executeSafe(args) }

    protected abstract suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?>

    companion object {
        const val ERROR_KEY: String = "error"
        const val RESULT_KEY: String = "result"
    }
}

internal suspend fun spotifyCall(block: suspend () -> Map<String, Any?>): Map<String, Any?> =
    withContext(Dispatchers.IO) {
        runCatching { block() }
            .getOrElse { mapOf(SpotifyTool.ERROR_KEY to (it.message ?: "Spotify API call failed")) }
    }

internal fun strArg(args: Map<String, Any?>, key: String): String? =
    (args[key] as? String)?.takeIf(String::isNotBlank)

internal fun intArg(args: Map<String, Any?>, key: String, default: Int): Int =
    (args[key] as? Number)?.toInt() ?: default

internal fun boolArg(args: Map<String, Any?>, key: String, default: Boolean): Boolean =
    (args[key] as? Boolean) ?: default

internal fun listArg(args: Map<String, Any?>, key: String): List<String> =
    (args[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
