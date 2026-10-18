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

                        Design for a compact card inside a mobile chat, with roughly 280-360 dp of available width.
                        Prefer a simple vertical hierarchy: short title, optional supporting text, content, then actions.
                        Use one outer Card at most; avoid nested cards, repeated section headings and decorative dividers.
                        Use a modest heading variant supported by the catalog, body text for content, and caption for metadata.
                        Avoid h1/h2-sized headings in chat cards. Keep source, format and duration on one concise metadata line.
                        Follow the host theme; do not invent colors, fonts, spacing or sizing properties absent from the catalog.
                        Give each card one primary action. Use secondary actions sparingly and label them with short, clear verbs.
                        Put the primary action on its own row. Use at most two short text buttons in any other Row.
                        Stack longer actions vertically; never squeeze three or more text buttons across the card.
                        Keep button labels at normal reading size; never insert line breaks or split words to make them fit.
                        Use the user's language consistently, including action labels, status text and field descriptions.
                        Show each fact once; avoid repeating playback state, timestamps or instructions in multiple places.
                        Prefer catalog Video or AudioPlayer components for media instead of recreating their controls with buttons.
                        Only show controls and status supported by actual supplied capabilities and facts.
                        Never invent playing/paused state, progress, volume controls or success messages from a URL or button click.
                        For an external-player flow, show concise media details and an open/play entry instead of simulated controls.
                        For forms, place labels with their fields, initialize bound values, and end with a specific primary action.
                        Minimize typing by choosing input components that match the meaning of each field.
                        REQUIRED INPUT SELECTION: when the catalog contains DateTimeInput, use it for calendar dates and clock times.
                        This applies to alarms, reminders, appointments, reservations, birthdays, and start/end dates or times.
                        Do NOT use TextField for these values, including number fields labeled hour/minute or separate date parts.
                        Do NOT require users to type HH:mm, YYYY-MM-DD, or ISO strings; the picker handles those values.
                        Clock time: enableTime=true, enableDate=false. Calendar date: enableDate=true, enableTime=false.
                        A scheduled date and time: enableDate=true, enableTime=true. Never leave both flags false.
                        Bind value to a writable path and initialize that path in data with a valid supported ISO value or an empty string.
                        Example clock-time component (adapt the visible label to the user's language):
                        {"id":"alarm-time","component":"DateTimeInput","label":"时间","value":{"path":"/alarmTime"},"enableDate":false,"enableTime":true}
                        Example initial data when the time is unknown: {"alarmTime":""}.
                        Relative durations such as a number of minutes are not clock times; use an appropriate duration input.
                        Use a TextField for a calendar date/clock time only if the user explicitly requests manual text entry,
                        or DateTimeInput is absent from the supplied catalog. A previous text-based layout is not an exception.
                        For a finite set of choices, prefer ChoicePicker; for boolean settings, prefer CheckBox.
                        For bounded numeric adjustments, prefer Slider when appropriate; use TextField when precise entry is necessary.
                        Use supported initial values from the supplied context to reduce repeated input, without guessing missing facts.
                        Bind selections to writable data paths and use only components and settings present in the supplied catalog.
                        On update, preserve the existing layout and submitted values unless the requested change requires otherwise.
                        On update, replace existing date/time TextFields with DateTimeInput while preserving the known selected value.
                        FINAL CHECK: inspect every date/time field before render_a2ui. Replace forbidden TextFields with DateTimeInput,
                        verify enableDate/enableTime, and verify each value binding has an initialized data entry.
                        Before calling render_a2ui, review the tree for narrow-screen readability and remove redundant elements.
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
