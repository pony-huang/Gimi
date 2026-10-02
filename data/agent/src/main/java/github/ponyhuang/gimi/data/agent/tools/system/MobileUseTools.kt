package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.data.agent.tools.modelRuntimeMetadataOrNull
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Shizuku 运行时自动声明的副屏工具集；共享任务归属和一次性截图注入。 */
@Singleton
class MobileUseTools @Inject constructor(private val repository: MobileUseRepository) : Toolset {
    private val observationProperties = mapOf(
        "observationId" to string("ID of the latest observation. Check availableActionModes. Old or consumed observations are rejected."),
    )
    private val pendingImages = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 8
    }

    private val tools = listOf(
        action("mobile_observe", "Wait up to about 8 seconds for a quiet screen; return screenshot, observationId, elements and explicit observation status. Quiet does not prove business loading is complete.") { owner, _ ->
            repository.observe(owner)
        },
        action(
            "mobile_click", "Click an element from the latest observation when its source is allowed by availableActionModes. Native elements use revalidated node actions; OCR text-box taps require settled pixels and are not verified buttons. Stale targets are rejected. Do not repeat a delivered or unknown action because observation timed out.",
            observationProperties + mapOf("elementId" to string("Element ID from nodes in the latest observation.")),
            listOf("observationId", "elementId"),
        ) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            val element = args["elementId"] as? String ?: return@action invalid("elementId required.")
            repository.click(owner, observation, element)
        },
        action(
            "mobile_set_text", "Replace text using an element supporting set_text in the latest observation. OCR text boxes cannot accept native text input. Does not invoke an input method.",
            observationProperties + mapOf("elementId" to string("Editable native element ID."), "text" to string("Replacement text; empty clears the field.")),
            listOf("observationId", "elementId", "text"),
        ) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            val element = args["elementId"] as? String ?: return@action invalid("elementId required.")
            val text = args["text"] as? String ?: return@action invalid("text must be a string.")
            repository.setText(owner, observation, element, text)
        },
        action(
            "mobile_open_app", "Launch an installed app by exact package name on the isolated secondary display.",
            mapOf("packageName" to string("Exact Android package name.")), listOf("packageName"),
        ) { owner, args ->
            val packageName = (args["packageName"] as? String)?.trim().orEmpty()
            if (!packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) invalid("Valid packageName required.")
            else repository.launch(owner, packageName)
        },
        action(
            "mobile_tap", "Tap a pixel coordinate from the latest screenshot on the secondary display. Prefer mobile_tap_relative when screenshot rendering size is uncertain. Use mobile_type_text for text fields.",
            observationProperties + mapOf("x" to integer("X in the latest screenshot."), "y" to integer("Y in the latest screenshot.")),
            listOf("observationId", "x", "y"),
        ) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            val x = args.int("x") ?: return@action invalid("x must be an integer.")
            val y = args.int("y") ?: return@action invalid("y must be an integer.")
            repository.tap(owner, observation, x, y)
        },
        action(
            "mobile_tap_relative", "Tap a position relative to the full latest screenshot, avoiding UI preview scaling errors. Inspect the latest screenshot and avoid overlays covering the target.",
            observationProperties + mapOf(
                "xPermille" to integer("Horizontal position 0..1000; 0 is left, 1000 is right."),
                "yPermille" to integer("Vertical position 0..1000; 0 is top, 1000 is bottom."),
            ), listOf("observationId", "xPermille", "yPermille"),
        ) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            val x = args.int("xPermille") ?: return@action invalid("xPermille must be an integer.")
            val y = args.int("yPermille") ?: return@action invalid("yPermille must be an integer.")
            if (x !in 0..1000 || y !in 0..1000) invalid("Relative coordinates must be 0..1000.")
            else repository.tapRelative(owner, observation, x, y)
        },
        action(
            "mobile_swipe", "Swipe between pixel coordinates on the secondary display.",
            observationProperties + mapOf(
                "x1" to integer("Start X."), "y1" to integer("Start Y."),
                "x2" to integer("End X."), "y2" to integer("End Y."),
                "durationMs" to integer("Duration, 100..5000 ms; default 450."),
            ), listOf("observationId", "x1", "y1", "x2", "y2"),
        ) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            val x1 = args.int("x1") ?: return@action invalid("x1 must be an integer.")
            val y1 = args.int("y1") ?: return@action invalid("y1 must be an integer.")
            val x2 = args.int("x2") ?: return@action invalid("x2 must be an integer.")
            val y2 = args.int("y2") ?: return@action invalid("y2 must be an integer.")
            val duration = if ("durationMs" in args) args.int("durationMs")
                ?: return@action invalid("durationMs must be an integer.") else 450
            if (duration !in 100..5000) invalid("durationMs must be 100..5000.")
            else repository.swipe(owner, observation, x1, y1, x2, y2, duration)
        },
        action("mobile_back", "Send Back once from the latest observation.", observationProperties, listOf("observationId")) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            repository.back(owner, observation)
        },
        action(
            "mobile_type_text", "Replace text in a secondary-display field at a screenshot coordinate. If direct replacement fails, focus the field with one tap and retry once. Does not invoke an input method.",
            observationProperties + mapOf(
                "x" to integer("X inside the text field in the latest screenshot."),
                "y" to integer("Y inside the text field in the latest screenshot."),
                "text" to string("Replacement text; an empty string clears the field."),
            ), listOf("observationId", "x", "y", "text"),
        ) { owner, args ->
            val observation = args.observationId() ?: return@action invalid("observationId required.")
            val x = args.int("x") ?: return@action invalid("x must be an integer.")
            val y = args.int("y") ?: return@action invalid("y must be an integer.")
            val value = args["text"] as? String ?: return@action invalid("text must be a string.")
            repository.typeText(owner, observation, x, y, value)
        },
        action(
            "mobile_wait", "Wait before checking the secondary display again. Use during downloads or installation; this does not capture a screenshot or change the display. Call mobile_observe afterward.",
            mapOf("seconds" to integer("Wait duration in seconds, 1..60.")), listOf("seconds"),
        ) { _, args ->
            val seconds = (args["seconds"] as? Number)?.toDouble()
                ?.takeIf { it in 1.0..60.0 && it % 1.0 == 0.0 }
                ?.toInt() ?: return@action invalid("seconds must be an integer from 1..60.")
            delay((seconds * 1_000L).milliseconds)
            mapOf(
                "status" to "waited",
                "message" to "Waited ${seconds}s; call mobile_observe to inspect the latest screen.",
                "seconds" to seconds,
            )
        },
        action("mobile_stop", "Finish this execution's display use. Keep the background app open for the user and future turns in this chat.") { owner, _ ->
            repository.finishExecution(owner)
        },
    )

    fun all(): List<FunctionTool> = tools

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        if (availableFor(readonlyContext)) tools else emptyList()

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
        if (!availableFor(toolContext.context)) return llmRequest
        return llmRequest.appendInstructions(
            Content(
                parts = listOf(Part(text = """
                    <mobile_use>
                    Use mobile_* tools only when the user asks you to operate or inspect an installed Android app.
                    They operate a background display associated with this chat. The user may also
                    view and operate that app; user input can change the page between observations.
                    When the app package is known, start with mobile_open_app and use its screenshot;
                    an empty display before launch may have no frame. Prefer mobile_click with an
                    elementId from nodes, and mobile_set_text for nodes supporting set_text. OCR
                    nodes are text boxes, not verified buttons. Use mobile_tap_relative as a visual
                    fallback. All navigation/input actions require the latest observationId.
                    Never reuse an observation after an action. If stale_observation is returned,
                    observe again. If actionStatus is delivered or unknown, do not repeat an action
                    because observation failed or timed out; call mobile_observe instead.
                    observationStatus=settled means quiet pixels, not business loading complete.
                    Check availableActionModes: pixel_coordinates is required for coordinates or
                    OCR; native_elements allows revalidated native actions even during animations.
                    If no applicable mode is available, observe again; back is available for navigation.
                    Inspect each
                    returned screenshot, including overlays, quantities and totals, before continuing.
                    mobile_type_text allows one focus retry only after explicit rejection of direct
                    replacement. Honor the user's stopping point for submitting or paying.
                    For login, passwords, verification codes, and sensitive confirmation steps,
                    ask the user to complete them directly in the background app window. Do not
                    request passwords or verification codes in chat or tool arguments. Observe
                    the page again before continuing after user input; do not assume the prior
                    input field or page remains active. Honor the user's consent for sensitive actions.
                    Wait for progress when needed, and call mobile_stop when done. This releases
                    only this execution; the app stays open until the user closes it. If session_closed
                    is returned, do not reopen during this execution. Other chats must first close
                    the existing background app.
                    If a tool reports that Shizuku permission or device support is unavailable, explain the
                    required setup instead of retrying the same action.
                    </mobile_use>
                """.trimIndent())),
            ),
        )
    }

    private fun availableFor(context: ReadonlyContext?): Boolean =
        context.modelRuntimeMetadataOrNull()?.supportsImages == true &&
            repository.availability() in setOf(
                MobileUseAvailability.READY,
                MobileUseAvailability.BUSY,
                MobileUseAvailability.PERMISSION_REQUIRED,
                MobileUseAvailability.PERMISSION_DENIED,
            )

    private fun action(
        name: String,
        description: String,
        properties: Map<String, Schema> = emptyMap(),
        required: List<String> = emptyList(),
        operation: suspend (String, Map<String, Any?>) -> Any,
    ): FunctionTool = object : FunctionTool(name = name, description = description) {
        override fun declaration(): FunctionDeclaration = FunctionDeclaration(
            name = name,
            description = description,
            parameters = Schema(type = Type.OBJECT, properties = properties, required = required),
        )

        override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
            val owner = ToolRunMetadata.mobileUseOwner(context.context.runConfig?.customMetadata)
                ?: return invalid("Agent task identity unavailable.", "unavailable")
            val result = operation(owner, args)
            return if (result is MobileUseResult) result.asToolResponse() else result
        }

        override suspend fun processLlmRequest(toolContext: ToolContext, llmRequest: LlmRequest): LlmRequest {
            val request = super.processLlmRequest(toolContext, llmRequest)
            val response = request.contents.lastOrNull()?.parts
                ?.firstNotNullOfOrNull { it.functionResponse?.takeIf { call -> call.name == name }?.response }
                ?: return request
            val token = response["imageToken"] as? String ?: return request
            val jpeg = synchronized(pendingImages) { pendingImages.remove(token) } ?: return request
            return request.appendContent(
                Content(
                    role = Role.USER,
                    parts = listOf(
                        Part(text = "Screenshot (${response["width"]}x${response["height"]}), observation=${response["observationId"]}, state=${response["observationStatus"]}, frameAgeMs=${response["frameAgeMs"]}; actionStatus=${response["actionStatus"]}:"),
                        Part(inlineData = Blob(mimeType = "image/jpeg", displayName = "mobile-use.jpg", data = jpeg)),
                    ),
                ),
            )
        }
    }

    private fun MobileUseResult.asToolResponse(): Map<String, Any> = buildMap {
        put("status", status)
        put("message", message)
        put("actionStatus", actionStatus)
        displayId?.let { put("displayId", it) }
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        observation?.let { snapshot ->
            put("observationId", snapshot.id)
            put("observationStatus", snapshot.state)
            put("observationReason", snapshot.reason)
            put("coordinateSystem", "screenshot_pixels")
            snapshot.frameAgeMs?.let { put("frameAgeMs", it) }
            snapshot.frameSequence?.let { put("frameSequence", it) }
            put("nodesStatus", snapshot.nodesStatus)
            put("nodesTruncated", snapshot.truncated)
            put("availableActionModes", snapshot.actionModes)
            put("nodes", snapshot.elements.map { element -> buildMap<String, Any> {
                put("elementId", element.id)
                put("source", element.source)
                put("bounds", listOf(element.bounds.left, element.bounds.top, element.bounds.right, element.bounds.bottom))
                put("actions", element.actions)
                put("enabled", element.enabled)
                put("editable", element.editable)
                put("scrollable", element.scrollable)
                element.clickable?.let { put("clickable", it) }
                element.text?.let { put("text", it) }
                element.description?.let { put("description", it) }
                element.resourceId?.let { put("resourceId", it) }
                element.className?.let { put("className", it) }
                element.packageName?.let { put("packageName", it) }
                element.windowId?.let { put("windowId", it) }
                element.windowLayer?.let { put("windowLayer", it) }
                element.parentId?.let { put("parentId", it) }
                element.confidence?.let { put("confidence", it) }
            } })
        }
        imageJpeg?.let { bytes ->
            val token = UUID.randomUUID().toString()
            synchronized(pendingImages) { pendingImages[token] = bytes }
            put("imageToken", token)
            put("imageStatus", "Screenshot attached to the next model request only.")
        }
    }

    private fun Map<String, Any?>.observationId(): String? = (get("observationId") as? String)?.takeIf { it.isNotBlank() }
    private fun Map<String, Any?>.int(key: String): Int? = (get(key) as? Number)?.toDouble()
        ?.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
    private fun string(description: String) = Schema(type = Type.STRING, description = description)
    private fun integer(description: String) = Schema(type = Type.INTEGER, description = description)
    private fun invalid(message: String, status: String = "invalid_argument") = mapOf("status" to status, "message" to message, "actionStatus" to "not_sent")
}
