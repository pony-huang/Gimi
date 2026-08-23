package github.ponyhuang.gimi.agent.tools.official.glm

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type

/**
 * Locally executed GLM web search tool backed by [GlmWebToolApi].
 *
 * Unlike the declaration-only OpenAI/Anthropic web search (executed remotely by
 * the provider), GLM exposes search as a standalone endpoint, so this tool
 * performs the HTTP call itself and returns structured results to the model.
 */
internal class GlmWebSearchTool(
    private val api: GlmWebToolApi,
) : FunctionTool(name = NAME, description = DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = PARAMETERS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val query = (args[ARG_QUERY] as? String)?.takeIf(String::isNotBlank)
            ?: return mapOf(ERROR_KEY to "Missing required argument: $ARG_QUERY")
        return runCatching {
            val results = api.search(
                query = query,
                // JSON 反序列化可能把整数参数给成 Double，统一按 Number 处理。
                count = (args[ARG_COUNT] as? Number)?.toInt(),
                recencyFilter = args[ARG_RECENCY] as? String,
                contentSize = args[ARG_CONTENT_SIZE] as? String,
            )
            mapOf(RESULT_KEY to results.map { it.toResultMap() })
        }.getOrElse { mapOf(ERROR_KEY to (it.message ?: "GLM web search failed")) }
    }

    private fun GlmSearchResult.toResultMap(): Map<String, String> = mapOf(
        "title" to title,
        "link" to link,
        "content" to content,
        "media" to media,
        "publish_date" to publishDate,
    )

    companion object {
        const val NAME = "web_search"
        private const val DESCRIPTION =
            "搜索互联网获取最新信息，返回标题、链接、来源与内容摘要。"
        private const val ERROR_KEY = "error"
        private const val RESULT_KEY = "results"
        private const val ARG_QUERY = "search_query"
        private const val ARG_COUNT = "count"
        private const val ARG_RECENCY = "search_recency_filter"
        private const val ARG_CONTENT_SIZE = "content_size"

        private val PARAMETERS = Schema(
            type = Type.OBJECT,
            description = "GLM 网页搜索参数",
            properties = mapOf(
                ARG_QUERY to Schema(
                    type = Type.STRING,
                    description = "搜索内容，建议不超过 70 个字符",
                ),
                ARG_COUNT to Schema(
                    type = Type.INTEGER,
                    description = "返回条数，范围 1-50，默认 10",
                ),
                ARG_RECENCY to Schema(
                    type = Type.STRING,
                    description = "搜索时间范围，默认 noLimit",
                    enum = listOf("oneDay", "oneWeek", "oneMonth", "oneYear", "noLimit"),
                ),
                ARG_CONTENT_SIZE to Schema(
                    type = Type.STRING,
                    description = "返回内容长度：medium 为摘要，high 为详细内容",
                    enum = listOf("medium", "high"),
                ),
            ),
            required = listOf(ARG_QUERY),
        )
    }
}

/** Locally executes GLM webpage reading and returns the parsed page to the model. */
internal class GlmReaderTool(
    private val api: GlmWebToolApi,
) : FunctionTool(name = NAME, description = DESCRIPTION) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = PARAMETERS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val url = (args[ARG_URL] as? String)?.takeIf(String::isNotBlank)
            ?: return mapOf(ERROR_KEY to "Missing required argument: $ARG_URL")
        return runCatching {
            val result = api.reader(
                url = url,
                // JSON 反序列化可能把整数参数给成 Double，统一按 Number 处理。
                timeout = (args[ARG_TIMEOUT] as? Number)?.toInt(),
                noCache = args[ARG_NO_CACHE] as? Boolean,
                returnFormat = args[ARG_RETURN_FORMAT] as? String,
            )
            mapOf(RESULT_KEY to result.toResultMap())
        }.getOrElse { mapOf(ERROR_KEY to (it.message ?: "GLM reader failed")) }
    }

    private fun GlmReaderResult.toResultMap(): Map<String, String> = mapOf(
        "title" to title,
        "url" to url,
        "description" to description,
        "content" to content,
    )

    companion object {
        const val NAME = "web_reader"
        private const val DESCRIPTION =
            "读取并解析指定网页，返回标题、描述和正文内容。"
        private const val ERROR_KEY = "error"
        private const val RESULT_KEY = "result"
        private const val ARG_URL = "url"
        private const val ARG_TIMEOUT = "timeout"
        private const val ARG_NO_CACHE = "no_cache"
        private const val ARG_RETURN_FORMAT = "return_format"

        private val PARAMETERS = Schema(
            type = Type.OBJECT,
            description = "GLM 网页阅读参数",
            properties = mapOf(
                ARG_URL to Schema(
                    type = Type.STRING,
                    description = "需要读取的网页 URL",
                ),
                ARG_TIMEOUT to Schema(
                    type = Type.INTEGER,
                    description = "请求超时时间（秒），默认 20",
                ),
                ARG_NO_CACHE to Schema(
                    type = Type.BOOLEAN,
                    description = "是否禁用缓存，默认 false",
                ),
                ARG_RETURN_FORMAT to Schema(
                    type = Type.STRING,
                    description = "返回格式，默认 markdown",
                    enum = listOf("markdown", "text"),
                ),
            ),
            required = listOf(ARG_URL),
        )
    }
}
