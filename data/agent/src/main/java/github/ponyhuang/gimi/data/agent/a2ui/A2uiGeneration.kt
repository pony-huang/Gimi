package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.*
import github.ponyhuang.gimi.domain.a2ui.*
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*

/** 只生成界面的隔离子 Agent；最多一次纠错，不执行主 Agent 的业务工具。 */
internal class A2uiGeneration(private val model: Model, private val protocol: A2uiProtocol) {
    suspend fun generate(
        surfaceId: String,
        capabilities: A2uiCapabilities,
        instruction: String,
        requestedComponents: Set<String> = emptySet(),
        publishPreview: (suspend (A2uiDocument) -> Unit)? = null,
    ): A2uiDocument {
        var correction = ""
        val scoped = generationCatalog(capabilities, requestedComponents)
        val allowedTypes = Json.parseToJsonElement(scoped.schemaJson).jsonObject["components"]?.jsonObject?.keys
        val inputRouting = inputRoutingInstructions(scoped)
        if (publishPreview != null) {
            try {
                return streamSurface(surfaceId, capabilities, scoped, allowedTypes, inputRouting, instruction, publishPreview)
            } catch (invalid: InvalidA2uiStream) {
                correction = "\nPrevious output was invalid: ${invalid.message}. Generate a corrected complete surface."
            }
        }
        val attempts = if (publishPreview == null) 2 else 1
        repeat(attempts) { attempt ->
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
                        Button variants are primary, default, and borderless; never use a variant named secondary.
                        Do not include id or component inside a nested properties object: components are flat objects.

                        Design for a compact card inside a mobile chat, with roughly 280-360 dp of available width.
                        Prefer a simple vertical hierarchy: short title, optional supporting text, content, then actions.
                        Use one outer Card at most; avoid nested cards, repeated section headings and decorative dividers.
                        Use a modest heading variant supported by the catalog, body text for content, and caption for metadata.
                        Avoid h1/h2-sized headings in chat cards. Keep source, format and duration on one concise metadata line.
                        Follow the host theme; do not invent colors, fonts, spacing or sizing properties absent from the catalog.
                        Give each card one primary action. Use secondary actions sparingly and label them with short, clear verbs.
                        Put one or two short footer actions in a Row with justify="end" and align="center".
                        Keep the containing Column aligned with align="stretch"; keep body content start-aligned.
                        Stack longer actions vertically; never squeeze three or more text buttons across the card.
                        Keep button labels at normal reading size; never insert line breaks or split words to make them fit.
                        Use the user's language consistently, including action labels, status text and field descriptions.
                        Show each fact once; avoid repeating playback state, timestamps or instructions in multiple places.
                        Prefer catalog Video or AudioPlayer components for media instead of recreating their controls with buttons.
                        Only show controls and status supported by actual supplied capabilities and facts.
                        Never invent playing/paused state, progress, volume controls or success messages from a URL or button click.
                        For an external-player flow, show concise media details and an open/play entry instead of simulated controls.
                        $inputRouting
                        Before calling render_a2ui, review the tree for narrow-screen readability and remove redundant elements.
                        Catalog: ${scoped.schemaJson}
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
                val document = surfaceDocument(surfaceId, capabilities, allowedTypes, components, data)
                protocol.validate(document, capabilities)
                return document
            } catch (invalid: IllegalArgumentException) {
                if (attempt == attempts - 1) throw invalid
                correction = "\nPrevious output was invalid: ${invalid.message}. Generate a corrected complete surface."
            }
        }
        error("Unreachable")
    }

    /** 每行是自包含快照，只有通过完整校验的预览才进入聊天。 */
    private suspend fun streamSurface(
        surfaceId: String,
        capabilities: A2uiCapabilities,
        scoped: A2uiCapabilities,
        allowedTypes: Set<String>?,
        inputRouting: String,
        instruction: String,
        publishPreview: suspend (A2uiDocument) -> Unit,
    ): A2uiDocument {
        val request = LlmRequest(
            model = model,
            contents = listOf(Content(role = Role.USER, parts = listOf(Part(text = instruction)))),
            config = GenerateContentConfig(systemInstruction = Content(parts = listOf(Part(text = """
                Generate a compact native A2UI 0.9.1 chat surface in the user's language.
                Output exactly two newline-terminated JSON Lines, without fences or prose:
                {"final":false,"components":[...],"data":{...}}
                {"final":true,"components":[...],"data":{...}}
                Write the preview immediately, before planning the details. Keep it small: show a factual title or short summary using the supplied facts.
                Each line is a complete snapshot with a root id of "root", unique ids and all referenced children.
                Use a read-only preview and keep its root, layout and retained component ids in the final snapshot.
                The final line must include all content and actions. End each line with a newline.
                Use only the supplied catalog. Keep components flat and use Button event actions for business requests.
                $inputRouting
                Catalog: ${scoped.schemaJson}
            """.trimIndent())))),
        )
        var previewSeen = false
        var sawTextDelta = false
        var final: A2uiDocument? = null
        val line = StringBuilder()
        suspend fun acceptLine(raw: String) {
            if (raw.isBlank()) return
            val (isFinal, document) = try {
                require(final == null) { "Unexpected snapshot after final" }
                val payload = Json.parseToJsonElement(raw).jsonObject
                val isFinal = payload.getValue("final").jsonPrimitive.boolean
                require(isFinal || !previewSeen) { "Only one preview is allowed" }
                val document = surfaceDocument(
                    surfaceId, capabilities, allowedTypes,
                    payload.getValue("components").jsonArray, payload.getValue("data").jsonObject,
                )
                protocol.validate(document, capabilities)
                isFinal to document
            } catch (invalid: IllegalArgumentException) {
                throw InvalidA2uiStream(invalid.message)
            } catch (invalid: NoSuchElementException) {
                throw InvalidA2uiStream(invalid.message)
            } catch (invalid: IllegalStateException) {
                throw InvalidA2uiStream(invalid.message)
            }
            if (isFinal) final = document else {
                previewSeen = true
                publishPreview(document)
            }
        }
        model.generateContent(request, stream = true).takeWhile { response ->
            if (response.errorCode != null || !response.errorMessage.isNullOrBlank()) {
                error(response.errorMessage ?: response.errorCode.orEmpty())
            }
            for (part in response.content?.parts.orEmpty()) {
                val text = part.text?.takeIf { it.isNotEmpty() && part.thought != true } ?: continue
                // ADK 适配器可能在增量之后再发一次完整聚合文本，不能重复拼接。
                if (response.partial != true && sawTextDelta) continue
                if (response.partial == true) sawTextDelta = true
                for (character in text) {
                    if (character == '\n') {
                        acceptLine(line.toString())
                        line.setLength(0)
                    } else {
                        if (line.length >= 256 * 1024) throw InvalidA2uiStream("Snapshot is too large")
                        line.append(character)
                    }
                }
            }
            // 最终行已校验，无需等传输发送聚合副本或结束信号。
            final == null
        }.collect {}
        acceptLine(line.toString())
        return final ?: throw InvalidA2uiStream("Missing final snapshot")
    }

    private fun surfaceDocument(
        surfaceId: String,
        capabilities: A2uiCapabilities,
        allowedTypes: Set<String>?,
        components: JsonArray,
        data: JsonObject,
    ): A2uiDocument {
        require(allowedTypes == null || components.all { it.componentType() in allowedTypes }) {
            "Use only component types from the selected catalog"
        }
        return A2uiDocument(surfaceId, listOf(
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
    }

    private class InvalidA2uiStream(message: String?) : IllegalArgumentException(message)

    private val renderDeclaration = FunctionDeclaration(
        name = "render_a2ui",
        description = "Return a complete native A2UI surface.",
        parameters = Schema(type = Type.OBJECT, properties = mapOf(
            "components" to Schema(type = Type.STRING, description = "JSON array of flat A2UI components."),
            "data" to Schema(type = Type.STRING, description = "JSON object containing initial bound field values."),
        ), required = listOf("components", "data")),
    )
}

private fun JsonElement.componentType(): String? =
    (this as? JsonObject)?.get("component")?.jsonPrimitive?.contentOrNull

internal fun message(operation: String, payload: JsonObject): String = buildJsonObject {
    put("version", "v0.9.1")
    put(operation, payload)
}.toString()
