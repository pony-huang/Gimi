package github.ponyhuang.gimi.data.agent.tools.official.deepseek

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 将 DeepSeek 搜索与抓取适配为可跨模型执行的普通函数；参数错误不发起付费请求。 */
internal class DeepSeekWebTool(
    toolId: String,
    private val api: DeepSeekWebToolApi,
) : FunctionTool(
    name = toolId,
    description = if (toolId == OfficialToolIds.DEEPSEEK_WEB_SEARCH) SEARCH_DESCRIPTION else FETCH_DESCRIPTION,
) {
    init {
        require(toolId in setOf(OfficialToolIds.DEEPSEEK_WEB_SEARCH, OfficialToolIds.DEEPSEEK_WEB_FETCH))
    }

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = if (name == OfficialToolIds.DEEPSEEK_WEB_SEARCH) SEARCH_PARAMETERS else FETCH_PARAMETERS,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        cancellationAwareRunCatching {
            if (name == OfficialToolIds.DEEPSEEK_WEB_SEARCH) {
                api.search(searchPayload(args))
            } else {
                api.fetch(fetchPayload(args))
            }
        }.getOrElse { mapOf("error" to (it.message ?: "DeepSeek web tool failed")) }

    private fun searchPayload(args: Map<String, Any?>): JsonObject = buildJsonObject {
        put("query", requiredText(args, "query", 128))
        if ("count" in args) {
            val value = args["count"]
            val number = (value as? Number)?.toDouble()
            require(number != null && number.isFinite() && number % 1.0 == 0.0 && number in 1.0..50.0) {
                "count must be an integer between 1 and 50"
            }
            put("count", number.toInt())
        }
        args["ori_question"]?.let { put("ori_question", requiredText(args, "ori_question", 128)) }
        if ("localization" in args) {
            val value = args["localization"]
            require(value is String) { "localization must be a string" }
            put("localization", value)
        }
        if ("need_content" in args) {
            val value = args["need_content"]
            require(value is Boolean) { "need_content must be a boolean" }
            put("need_content", value)
        }
    }

    private fun fetchPayload(args: Map<String, Any?>): JsonObject = buildJsonObject {
        val url = requiredText(args, "url", 8192)
        val parsed = url.toHttpUrlOrNull()
        require(parsed != null && parsed.username.isEmpty() && parsed.password.isEmpty() &&
            !url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').contains('@')) {
            "url must be an HTTP/HTTPS URL with a host and without credentials"
        }
        put("url", url)
    }

    private fun requiredText(args: Map<String, Any?>, key: String, maxLength: Int): String {
        val value = args[key] as? String
        require(value != null && value.isNotBlank() && value.codePointCount(0, value.length) <= maxLength) {
            "$key must contain 1-$maxLength Unicode characters and cannot be blank"
        }
        return value
    }

    private companion object {
        const val SEARCH_DESCRIPTION = "Search the web with DeepSeek for up-to-date information. Returns links, summaries, relevance scores, and optional page content."
        const val FETCH_DESCRIPTION = "Fetch a web page with DeepSeek and return its title and content in Markdown."
        val SEARCH_PARAMETERS = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "Search query, 1-128 Unicode characters. Supports site:, -site:, exclusions, exact phrases, lang:, after:, and before: operators."),
                "count" to Schema(type = Type.INTEGER, description = "Number of results to return, from 1 to 50. Defaults to 10."),
                "ori_question" to Schema(type = Type.STRING, description = "Original user question that triggered the search, 1-128 Unicode characters. Optional; null is treated as omitted."),
                "localization" to Schema(type = Type.STRING, description = "IP address used to prioritize localized search results. Defaults to an empty string."),
                "need_content" to Schema(type = Type.BOOLEAN, description = "Whether to include page content in each result. Defaults to false."),
            ),
            required = listOf("query"),
        )
        val FETCH_PARAMETERS = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "url" to Schema(type = Type.STRING, description = "HTTP or HTTPS URL of the page to fetch, up to 8192 characters. Must contain a host and must not include a username or password."),
            ),
            required = listOf("url"),
        )
    }
}
