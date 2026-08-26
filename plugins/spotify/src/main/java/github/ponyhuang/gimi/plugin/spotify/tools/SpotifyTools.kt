package github.ponyhuang.gimi.plugin.spotify.tools

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spotify 工具基类 — 统一 IO 调度、错误包装与 schema 声明。
 *
 * 子类只需实现 [executeSafe]；[declaration] 由构造传入的 [parameters] 自动生成，
 * 无需每个工具覆写。抛出的异常经 [spotifyCall] 包装为 `{error: msg}`，
 * 返回值为 JSON-native（Map/List/String/Number/Boolean/null）。
 */
internal abstract class SpotifyTool(
    name: String,
    description: String,
    private val parameters: Schema? = null,
) : FunctionTool(name = name, description = description) {

    override fun declaration(): FunctionDeclaration =
        FunctionDeclaration(name = name, description = description, parameters = parameters)

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        spotifyCall { executeSafe(args) }

    protected abstract suspend fun executeSafe(args: Map<String, Any?>): Map<String, Any?>

    companion object {
        const val ERROR_KEY: String = "error"
        const val RESULT_KEY: String = "result"
    }
}

/**
 * 无状态工具工厂：以「name + description + parameters + 处理函数」声明一个工具，
 * 省去每个工具一个 class 的样板代码。状态（[SpotifyApi] 等）由闭包捕获。
 */
internal fun spotifyTool(
    name: String,
    description: String,
    parameters: Schema? = null,
    block: suspend (Map<String, Any?>) -> Map<String, Any?>,
): SpotifyTool = object : SpotifyTool(name, description, parameters) {
    override suspend fun executeSafe(args: Map<String, Any?>) = block(args)
}

/** 声明一个 object 类型参数 schema（参数 + 必填列表）。 */
internal fun objectSchema(
    vararg properties: Pair<String, Schema>,
    required: List<String> = emptyList(),
): Schema = Schema(
    type = Type.OBJECT,
    properties = properties.toMap(),
    required = required.takeIf { it.isNotEmpty() },
)

/** 声明一个 string 参数 schema，可带 enum 取值。 */
internal fun stringParam(
    description: String,
    enum: List<String>? = null,
): Schema = Schema(
    type = Type.STRING,
    description = description,
    enum = enum,
)

/** 声明一个 int 参数 schema（对齐官方 minimum/maximum 约束）。 */
internal fun intParam(
    description: String,
    min: Int? = null,
    max: Int? = null,
): Schema = Schema(
    type = Type.INTEGER,
    description = description,
    minimum = min?.toDouble(),
    maximum = max?.toDouble(),
)

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
