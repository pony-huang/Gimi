package github.ponyhuang.gimi.data.agent.tools.official.kimi

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolIds
import java.time.DateTimeException
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Kimi 三个独立联网接口的函数声明与入参校验；非法参数不会触发付费调用。 */
internal class KimiWebTool(
    functionId: String,
    private val api: KimiWebToolApi,
) : FunctionTool(name = functionId, description = descriptions.getValue(functionId)) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = parameters(name),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        cancellationAwareRunCatching {
            api.execute(name, payload(args))
        }.getOrElse { mapOf("error" to (it.message ?: "Kimi web tool failed")) }

    private fun payload(args: Map<String, Any?>): JsonObject = buildJsonObject {
        val allowed = parameters(name).properties.orEmpty().keys
        require(args.keys.all { it in allowed }) { "Unsupported Kimi web tool parameter" }
        if (name == OfficialToolIds.KIMI_FETCH) {
            val url = requiredText(args, "url")
            val parsed = url.toHttpUrlOrNull()
            require(parsed != null && parsed.username.isEmpty() && parsed.password.isEmpty() &&
                !url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').contains('@')) {
                "url must be an HTTP/HTTPS URL without credentials"
            }
            put("url", url)
        } else {
            put("text_query", requiredText(args, "text_query"))
            put("limit", integer(args, "limit", 5, 20))
            put("timeout_seconds", integer(args, "timeout_seconds", 30, 60))
            if (name == OfficialToolIds.KIMI_SEARCH) {
                if ("include_content" in args) {
                    val value = args["include_content"]
                    require(value is Boolean) { "include_content must be a boolean" }
                    put("include_content", value)
                }
            } else {
                if ("sites" in args) {
                    val sites = args["sites"]
                    require(sites is List<*> && sites.size <= 5 && sites.all {
                        it is String && it.isNotBlank() && it.none { c -> c.isWhitespace() || c in "()" }
                    }) { "sites must contain at most 5 nonempty strings without whitespace or parentheses" }
                    put("sites", JsonArray(sites.map { JsonPrimitive(it as String) }))
                }
                if ("time_window" in args) {
                    val window = args["time_window"]
                    require(window is Map<*, *> && window.keys.all { it in setOf("start", "end") }) {
                        "time_window must be an object containing start and/or end"
                    }
                    val dates = window.mapValues { (_, value) -> normalizedDate(value) }
                    val start = dates["start"]
                    val end = dates["end"]
                    require(start == null || end == null || !start.isAfter(end)) { "time_window start must not exceed end" }
                    put("time_window", buildJsonObject {
                        window.forEach { (key, value) -> put(key as String, value as String) }
                    })
                }
            }
        }
    }

    private fun requiredText(args: Map<String, Any?>, key: String): String {
        val value = args[key]
        require(value is String && value.isNotBlank()) { "$key must be a nonempty string" }
        return value
    }

    private fun integer(args: Map<String, Any?>, key: String, default: Int, max: Int): Int {
        if (key !in args) return default
        val value = (args[key] as? Number)?.toDouble()
        require(value != null && value.isFinite() && value % 1.0 == 0.0 && value in 1.0..max.toDouble()) {
            "$key must be an integer between 1 and $max"
        }
        return value.toInt()
    }

    private fun normalizedDate(value: Any?): LocalDate {
        require(value is String && Regex("[0-9]{4}(-[0-9]{2})?(-[0-9]{2})?").matches(value)) {
            "time_window dates must use YYYY, YYYY-MM or YYYY-MM-DD"
        }
        return try {
            // 官方按周期第一天比较，不把年/月上界扩展到周期末尾。
            LocalDate.parse(when (value.length) { 4 -> "$value-01-01"; 7 -> "$value-01"; else -> value })
        } catch (_: DateTimeException) {
            throw IllegalArgumentException("Invalid time_window date")
        }
    }

    private companion object {
        val descriptions = mapOf(
            OfficialToolIds.KIMI_SEARCH to "Search the web with Kimi Basic. Returns titles, links, summaries and optional full text. Use for result lists or collecting full pages.",
            OfficialToolIds.KIMI_SEARCH_PRO to "Search the web with Kimi Pro for questions, research and RAG. Returns relevant text chunks, scores, authority and source links. Write one specific query with entity, time and scope; avoid repeating near-identical paid searches.",
            OfficialToolIds.KIMI_FETCH to "Fetch a known HTTP/HTTPS page with Kimi. Returns its URL, title and Markdown body.",
        )

        fun parameters(name: String): Schema {
            if (name == OfficialToolIds.KIMI_FETCH) return Schema(
                type = Type.OBJECT,
                properties = mapOf("url" to Schema(type = Type.STRING, description = "HTTP/HTTPS URL to fetch.")),
                required = listOf("url"),
            )
            val properties = mutableMapOf(
                "text_query" to Schema(type = Type.STRING, description = "Nonempty search query. Submitted as-is; include specific entities, dates and qualifiers."),
                "limit" to Schema(type = Type.INTEGER, description = "Result limit, 1-20. Defaults to 5."),
                "timeout_seconds" to Schema(type = Type.INTEGER, description = "Server timeout in seconds, 1-60. Defaults to 30."),
            )
            if (name == OfficialToolIds.KIMI_SEARCH) {
                properties["include_content"] = Schema(type = Type.BOOLEAN, description = "Include full page text. Defaults to false.")
            } else {
                properties["sites"] = Schema(type = Type.ARRAY, items = Schema(type = Type.STRING), description = "At most 5 sites combined with OR; no blank entries, whitespace or parentheses.")
                properties["time_window"] = Schema(type = Type.OBJECT, properties = mapOf(
                    "start" to Schema(type = Type.STRING, description = "Optional lower date bound: YYYY, YYYY-MM or YYYY-MM-DD."),
                    "end" to Schema(type = Type.STRING, description = "Optional upper date bound, same formats. Bounds compare as the first day of their period; start must not exceed end."),
                ))
            }
            return Schema(type = Type.OBJECT, properties = properties, required = listOf("text_query"))
        }
    }
}
