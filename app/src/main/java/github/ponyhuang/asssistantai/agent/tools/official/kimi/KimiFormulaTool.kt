package github.ponyhuang.asssistantai.agent.tools.official.kimi

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

class KimiFormulaToolset(
    private val apiKey: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
) : Toolset {

    companion object {
        private val logger = LoggerFactory.getLogger(KimiFormulaToolset::class)
        private val FORMULA_URIS = listOf(
            "moonshot/convert:latest",
            "moonshot/web-search:latest",
            "moonshot/rethink:latest",
            "moonshot/random-choice:latest",
            "moonshot/mew:latest",
            "moonshot/memory:latest",
            "moonshot/excel:latest",
            "moonshot/date:latest",
            "moonshot/base64:latest",
            "moonshot/fetch:latest",
            "moonshot/quickjs:latest",
            "moonshot/code-runner:latest",
        )
    }
    private val toolCache = ConcurrentHashMap<Pair<String, String>, List<KimiFormulaTool>>()

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trimEnd('/')
            val seenNames = mutableSetOf<String>()
            coroutineScope {
                FORMULA_URIS.map { uri ->
                    async {
                        runCatching { loadTools(base, apiKey, uri) }
                            .onFailure { logger.warn(it) { "Failed to load Kimi formula: $uri" } }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
            }.flatten().filter { tool ->
                seenNames.add(tool.name).also { added ->
                    if (!added) logger.warn { "Skipping duplicate Kimi formula tool name: ${tool.name}" }
                }
            }
        }

    private fun loadTools(base: String, apiKey: String, uri: String): List<KimiFormulaTool> {
        return toolCache.computeIfAbsent(base to uri) {
            val request = Request.Builder()
                .url("$base/formulas/$uri/tools")
                .header("Authorization", "Bearer $apiKey")
                .build()

            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code} loading formula $uri" }
                val body = response.body?.string() ?: error("Empty body loading formula $uri")
                parseDeclarations(body).map { declaration ->
                    KimiFormulaTool(
                        baseUrl = base,
                        apiKey = apiKey,
                        formulaUri = uri,
                        declaration = declaration,
                        httpClient = httpClient
                    )
                }
            }
        }
    }

    private fun parseDeclarations(body: String): List<FormulaDeclaration> {
        val json = Json.parseToJsonElement(body).jsonObject
        val tools = json["tools"]?.jsonArray ?: return emptyList()

        return tools.mapNotNull { element ->
            val function = element.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
            val name = function["name"]?.jsonPrimitive?.content ?: return@mapNotNull null

            FormulaDeclaration(
                name = name,
                description = function["description"]?.jsonPrimitive?.content ?: name,
                parameters = function["parameters"]?.jsonObject?.toAdkSchema()
            )
        }
    }
}

internal data class FormulaDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema?,
)

internal class KimiFormulaTool(
    private val baseUrl: String,
    private val apiKey: String,
    private val formulaUri: String,
    private val declaration: FormulaDeclaration,
    private val httpClient: OkHttpClient
) : FunctionTool(
    name = declaration.name,
    description = declaration.description,
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val ERROR_KEY = "error"
        private const val RESULT_KEY = "result"
    }

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = declaration.parameters,
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any>): Any =
        withContext(Dispatchers.IO) {
            runCatching {
                executeFiber(args)
            }.getOrElse {
                mapOf(ERROR_KEY to (it.message ?: "Kimi formula execution failed"))
            }
        }

    private fun executeFiber(args: Map<String, Any>): Any {
        val payload = buildJsonObject {
            put("name", declaration.name)
            put("arguments", args.toJsonElement().toString())
        }

        val request = Request.Builder()
            .url("$baseUrl/formulas/$formulaUri/fibers")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code} executing $formulaUri: $body" }
            return parseFiberResult(body)
        }
    }

    private fun parseFiberResult(body: String): Any {
        val root = Json.parseToJsonElement(body).jsonObject
        val context = root["context"]?.jsonObject

        val error = root["error"]?.jsonPrimitive?.content
            ?: context?.get("error")?.jsonPrimitive?.content
        if (error != null) return mapOf(ERROR_KEY to error)

        val output = context?.get("output")?.jsonPrimitive?.content
            ?: context?.get("encrypted_output")?.jsonPrimitive?.content
            ?: return mapOf(ERROR_KEY to "Empty fiber output")

        return mapOf(RESULT_KEY to output)
    }
}


private fun JsonObject.toAdkSchema(): Schema {
    return Schema(
        description = this["description"]?.jsonPrimitive?.content,
        properties = this["properties"]?.jsonObject?.mapValues { (_, value) ->
            value.jsonObject.toAdkSchema()
        },
        items = this["items"]?.jsonObject?.toAdkSchema(),
        required = this["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
    )
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}