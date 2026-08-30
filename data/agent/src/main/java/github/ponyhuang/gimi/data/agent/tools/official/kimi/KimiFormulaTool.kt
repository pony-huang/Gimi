package github.ponyhuang.gimi.data.agent.tools.official.kimi

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
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

internal class KimiFormulaTool(
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

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        withContext(Dispatchers.IO) {
            runCatching {
                executeFiber(args)
            }.getOrElse {
                mapOf(ERROR_KEY to (it.message ?: "Kimi formula execution failed"))
            }
        }

    private fun executeFiber(args: Map<String, Any?>): Any {
        val payload = buildJsonObject {
            put("name", declaration.name)
            put("arguments", args.toJsonElement().toString())
        }

        val request = Request.Builder()
            .url("https://api.moonshot.cn/v1/formulas/${declaration.formulaUri}/fibers")
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
