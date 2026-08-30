package github.ponyhuang.gimi.data.agent.tools.official.kimi

import com.google.adk.kt.types.Schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One user-facing function exposed by a Moonshot formula URI.
 *
 * @property name Function name sent to the model.
 * @property description Human-readable capability description used for discovery.
 * @property parameters Optional ADK parameter declaration.
 * @property formulaUri Moonshot formula endpoint identifier used during execution.
 */
internal data class FormulaDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema?,
    val formulaUri: String,
)

/**
 * Vendor-neutral fetcher for the Moonshot formula manifest.
 *
 * Used both by [KimiFormulaToolset] (to build ADK `FunctionTool`s) and by the
 * function catalog implementation (to populate the user-selection UI). Network
 * access is parallelised across [FORMULA_URIS] with results deduplicated by
 * tool name; the first occurrence wins so the fiber endpoint URL stays stable
 * for a given tool name.
 */
internal class KimiFormulaManifest(
    private val apiKey: String,
    private val httpClient: OkHttpClient,
) {
    suspend fun fetch(): List<FormulaDeclaration> = withContext(Dispatchers.IO) {

        coroutineScope {
            FORMULA_URIS.map { uri ->
                async {
                    runCatching { load(uri) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
        }.flatten().deduplicate()
    }

    private fun load(uri: String): List<FormulaDeclaration> {
        val request = Request.Builder()
            .url("https://api.moonshot.cn/v1/formulas/$uri/tools")
            .header("Authorization", "Bearer $apiKey")
            .build()

        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} loading formula $uri" }
            val body = response.body?.string() ?: error("Empty body loading formula $uri")
            return parse(body, uri)
        }
    }

    private fun parse(body: String, formulaUri: String): List<FormulaDeclaration> {
        val json = Json.parseToJsonElement(body).jsonObject
        val tools = json["tools"]?.jsonArray ?: return emptyList()
        return tools.mapNotNull { element ->
            val function = element.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
            val name = function["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            FormulaDeclaration(
                name = name,
                description = function["description"]?.jsonPrimitive?.content ?: name,
                parameters = function["parameters"]?.jsonObject?.toAdkSchema(),
                formulaUri = formulaUri,
            )
        }
    }

    private fun List<FormulaDeclaration>.deduplicate(): List<FormulaDeclaration> {
        if (size < 2) return this
        val seen = mutableSetOf<String>()
        return filter { declaration -> seen.add(declaration.name) }
    }

    companion object {
        internal val FORMULA_URIS = listOf(
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
}

private fun JsonObject.toAdkSchema(): Schema = Schema(
    description = this["description"]?.jsonPrimitive?.content,
    properties = this["properties"]?.jsonObject?.mapValues { (_, value) ->
        value.jsonObject.toAdkSchema()
    },
    items = this["items"]?.jsonObject?.toAdkSchema(),
    required = this["required"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
)
