package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.pluginapi.PluginJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 知乎工具基类 — 持有 API 客户端与 access_secret 提供者，统一 IO 调度与错误处理。
 */
internal abstract class ZhihuTool(
    name: String,
    description: String,
    protected val api: ZhihuApi,
    private val secretProvider: () -> String,
) : FunctionTool(name = name, description = description) {

    protected suspend fun call(block: suspend (secret: String) -> Map<String, Any?>): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val secret = secretProvider().takeIf(String::isNotBlank)
                    ?: throw IllegalStateException("知乎插件未配置 access_secret")
                block(secret)
            }.getOrElse { mapOf(ERROR_KEY to (it.message ?: "知乎 API 调用失败")) }
        }

    companion object {
        const val ERROR_KEY: String = "error"
        const val RESULT_KEY: String = "result"
    }
}

internal fun intArg(args: Map<String, Any?>, key: String, default: Int): Int =
    (args[key] as? Number)?.toInt() ?: default

internal fun strArg(args: Map<String, Any?>, key: String): String? =
    (args[key] as? String)?.takeIf(String::isNotBlank)

/** 数组参数读取；ADK 把 JSON 数组转成 Kotlin List。 */
internal fun arrayArg(args: Map<String, Any?>, key: String): List<String> =
    (args[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

/** 构造 OBJECT Schema；required 为空时不输出。 */
internal fun objectSchema(
    vararg properties: Pair<String, Schema>,
    required: List<String> = emptyList(),
): Schema = Schema(
    type = Type.OBJECT,
    properties = properties.toMap(),
    required = required.takeIf { it.isNotEmpty() },
)

internal fun stringParam(description: String, enum: List<String>? = null): Schema =
    Schema(type = Type.STRING, description = description, enum = enum)

internal fun intParam(description: String, min: Int? = null, max: Int? = null): Schema =
    Schema(
        type = Type.INTEGER,
        description = description,
        minimum = min?.toDouble(),
        maximum = max?.toDouble(),
    )

internal fun arrayParam(description: String): Schema =
    Schema(type = Type.ARRAY, description = description, items = Schema(type = Type.STRING))

/** 站内搜索。 */
internal class ZhihuSearchTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "搜索关键词，不能为空"),
                "count" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，最大 10"),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val query = strArg(args, "query")
                ?: throw IllegalStateException("缺少参数 query")
            mapOf(RESULT_KEY to PluginJson.toNative(api.zhihuSearch(secret, query, intArg(args, "count", 10))))
        }

    companion object {
        const val NAME: String = "zhihu_search"
        private const val DESCRIPTION: String =
            "知乎站内搜索，返回匹配的问题、回答或文章（标题、摘要、作者、赞同数、链接等）。"
    }
}

/** 全网搜索。 */
internal class ZhihuGlobalSearchTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "搜索关键词，不能为空"),
                "count" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，最大 20"),
                "filter" to Schema(
                    type = Type.STRING,
                    description = "高级筛选表达式，如 host==\"example.com\" AND publish_time>=1778494631；可省略",
                ),
                "search_db" to Schema(
                    type = Type.STRING,
                    description = "搜索库：all / realtime / static，默认 all",
                    enum = listOf("all", "realtime", "static"),
                ),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val query = strArg(args, "query")
                ?: throw IllegalStateException("缺少参数 query")
            val result = api.globalSearch(
                secret = secret,
                query = query,
                count = intArg(args, "count", 10),
                filter = strArg(args, "filter"),
                searchDb = strArg(args, "search_db"),
            )
            mapOf(RESULT_KEY to PluginJson.toNative(result))
        }

    companion object {
        const val NAME: String = "zhihu_global_search"
        private const val DESCRIPTION: String =
            "知乎全网搜索，返回全网相关内容（标题、摘要、作者、来源、权威等级等）。"
    }
}

/** 热榜。 */
internal class ZhihuHotListTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 30，最大 30"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            mapOf(RESULT_KEY to PluginJson.toNative(api.hotList(secret, intArg(args, "limit", 30))))
        }

    companion object {
        const val NAME: String = "zhihu_hot_list"
        private const val DESCRIPTION: String =
            "获取知乎当前热榜内容（标题、摘要、封面、链接）。"
    }
}

/** 直答。 */
internal class ZhihuAskTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "要提问的内容，不能为空"),
                "model" to Schema(
                    type = Type.STRING,
                    description = "模型：fast / thinking / agent，默认 thinking",
                    enum = listOf("fast", "thinking", "agent"),
                ),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val query = strArg(args, "query")
                ?: throw IllegalStateException("缺少参数 query")
            val json = api.zhida(secret, modelId(strArg(args, "model")), query)
            val choice = json.optJSONArray("choices")?.optJSONObject(0)
            val message = choice?.optJSONObject("message")
            mapOf(
                RESULT_KEY to mapOf(
                    "content" to (message?.optString("content").orEmpty()),
                    "reasoning_content" to (message?.optString("reasoning_content").orEmpty()),
                    "finish_reason" to (choice?.optString("finish_reason").orEmpty()),
                ),
            )
        }

    private fun modelId(model: String?): String = when (model) {
        "fast" -> "zhida-fast-1p5"
        "agent" -> "zhida-agent"
        else -> "zhida-thinking-1p5"
    }

    companion object {
        const val NAME: String = "zhihu_ask"
        private const val DESCRIPTION: String =
            "向知乎直答提问，返回基于知乎内容的回答（含推理过程）。"
    }
}
