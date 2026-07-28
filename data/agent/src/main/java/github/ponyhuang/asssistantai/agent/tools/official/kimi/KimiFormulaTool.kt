package github.ponyhuang.asssistantai.agent.tools.official.kimi

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Exposes Moonshot formulas as ADK tools.
 *
 * @param enabledFunctionIds when non-null, only declarations whose `name` is in
 *   this set are registered. When null, every formula returned by the manifest
 *   is exposed (legacy / no-per-conversation-config behaviour).
 */
class KimiFormulaToolset(
    private val apiKey: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient,
    private val enabledFunctionIds: Set<String>? = null,
) : Toolset {

    companion object {
        private val logger = LoggerFactory.getLogger(KimiFormulaToolset::class)
    }

    private val manifest = KimiFormulaManifest(apiKey = apiKey, baseUrl = baseUrl, httpClient = httpClient)

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        withContext(Dispatchers.IO) {
            manifest.fetch()
                .map { declaration ->
                    KimiFormulaTool(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        declaration = declaration,
                        httpClient = httpClient,
                    )
                }
                .filter { tool ->
                    enabledFunctionIds == null || tool.name in enabledFunctionIds
                }
        }
}

internal class KimiFormulaTool(
    private val baseUrl: String,
    private val apiKey: String,
    private val declaration: FormulaDeclaration,
    private val httpClient: OkHttpClient,
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
            .url("$baseUrl/formulas/${declaration.formulaUri}/fibers")
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) { "HTTP ${response.code} executing ${declaration.formulaUri}: $body" }
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