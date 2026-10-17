package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.a2ui.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*

/** 只生成界面的隔离子 Agent；最多一次纠错，不执行主 Agent 的业务工具。 */
internal class A2uiGeneration(private val model: Model, private val protocol: A2uiProtocol) {
    suspend fun generate(
        surfaceId: String,
        capabilities: A2uiCapabilities,
        instruction: String,
    ): A2uiDocument {
        var correction = ""
        repeat(2) { attempt ->
            val request = LlmRequest(
                model = model,
                contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = instruction + correction)))),
                config = GenerateContentConfig(
                    systemInstruction = Content(parts = listOf(Part(text = """
                        Generate an A2UI 0.9.1 surface using ONLY the supplied catalog.
                        Call render_a2ui exactly once. components and data must be JSON strings.
                        Return a complete component tree, root id 'root', unique component ids.
                        Use the user's language for all visible labels. Bind editable values to data paths.
                        Buttons use event actions with meaningful names and context. Never execute business operations.
                        Do not include id or component inside a nested properties object: components are flat objects.
                        Catalog: ${capabilities.schemaJson}
                    """.trimIndent()))),
                    tools = listOf(Tool(functionDeclarations = listOf(renderDeclaration))),
                    toolConfig = ToolConfig(functionCallingConfig = FunctionCallingConfig(
                        mode = FunctionCallingConfigMode.ANY,
                        allowedFunctionNames = listOf("render_a2ui"),
                    )),
                ),
            )
            // 网络或 SDK 错误不进入格式纠错循环，避免失败时额外付费调用。
            val responses = model.generateContent(request, stream = false).toList()
            responses.firstOrNull { it.errorCode != null || !it.errorMessage.isNullOrBlank() }?.let {
                error(it.errorMessage ?: it.errorCode.orEmpty())
            }
            try {
                val call = responses.flatMap { it.content?.parts.orEmpty() }
                    .mapNotNull { it.functionCall }.singleOrNull { it.name == "render_a2ui" }
                    ?: throw IllegalArgumentException("Call render_a2ui exactly once")
                val components = Json.parseToJsonElement(call.args["components"] as? String
                    ?: throw IllegalArgumentException("components must be a JSON array string")).jsonArray
                val data = Json.parseToJsonElement(call.args["data"] as? String ?: "{}").jsonObject
                val document = A2uiDocument(surfaceId, listOf(
                    message("createSurface", buildJsonObject {
                        put("surfaceId", surfaceId); put("catalogId", capabilities.catalogId); put("sendDataModel", true)
                    }),
                    message("updateDataModel", buildJsonObject {
                        put("surfaceId", surfaceId); put("path", "/"); put("value", data)
                    }),
                    message("updateComponents", buildJsonObject {
                        put("surfaceId", surfaceId); put("components", components)
                    }),
                ))
                protocol.validate(document, capabilities)
                return document
            } catch (invalid: IllegalArgumentException) {
                if (attempt == 1) throw invalid
                correction = "\nPrevious output was invalid: ${invalid.message}. Generate a corrected complete surface."
            }
        }
        error("Unreachable")
    }

    private val renderDeclaration = FunctionDeclaration(
        name = "render_a2ui",
        description = "Return a complete native A2UI surface.",
        parameters = Schema(type = Type.OBJECT, properties = mapOf(
            "components" to Schema(type = Type.STRING, description = "JSON array of flat A2UI components."),
            "data" to Schema(type = Type.STRING, description = "JSON object containing initial bound field values."),
        ), required = listOf("components", "data")),
    )
}

internal fun message(operation: String, payload: JsonObject): String = buildJsonObject {
    put("version", "v0.9.1")
    put(operation, payload)
}.toString()
