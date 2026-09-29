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
    private val pendingImages = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 8
    }

    private val tools = listOf(
        action("mobile_observe", "Observe the isolated secondary display and return its current screenshot.") { owner, _ ->
            repository.observe(owner)
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
            "mobile_tap", "Tap a pixel coordinate on the secondary display. Do not tap text fields; use mobile_type_text at the field coordinate instead.",
            mapOf("x" to integer("X in the latest screenshot."), "y" to integer("Y in the latest screenshot.")),
            listOf("x", "y"),
        ) { owner, args ->
            val x = args.int("x") ?: return@action invalid("x must be an integer.")
            val y = args.int("y") ?: return@action invalid("y must be an integer.")
            repository.tap(owner, x, y)
        },
        action(
            "mobile_swipe", "Swipe between pixel coordinates on the secondary display.",
            mapOf(
                "x1" to integer("Start X."), "y1" to integer("Start Y."),
                "x2" to integer("End X."), "y2" to integer("End Y."),
                "durationMs" to integer("Duration, 100..5000 ms; default 450."),
            ), listOf("x1", "y1", "x2", "y2"),
        ) { owner, args ->
            val x1 = args.int("x1") ?: return@action invalid("x1 must be an integer.")
            val y1 = args.int("y1") ?: return@action invalid("y1 must be an integer.")
            val x2 = args.int("x2") ?: return@action invalid("x2 must be an integer.")
            val y2 = args.int("y2") ?: return@action invalid("y2 must be an integer.")
            val duration = args.int("durationMs") ?: 450
            if (duration !in 100..5000) invalid("durationMs must be 100..5000.")
            else repository.swipe(owner, x1, y1, x2, y2, duration)
        },
        action("mobile_back", "Send Back to the secondary display.") { owner, _ -> repository.back(owner) },
        action(
            "mobile_type_text", "Replace text in an editable secondary-display node at a screenshot coordinate without tapping the field first. Does not invoke an input method; fails when direct text replacement is unavailable.",
            mapOf(
                "x" to integer("X inside the text field in the latest screenshot."),
                "y" to integer("Y inside the text field in the latest screenshot."),
                "text" to string("Replacement text; an empty string clears the field."),
            ), listOf("x", "y", "text"),
        ) { owner, args ->
            val x = args.int("x") ?: return@action invalid("x must be an integer.")
            val y = args.int("y") ?: return@action invalid("y must be an integer.")
            val value = args["text"] as? String ?: return@action invalid("text must be a string.")
            repository.typeText(owner, x, y, value)
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
        action("mobile_stop", "Stop the owned secondary display and release its resources.") { owner, _ ->
            repository.stop(owner)
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
                    They act on an isolated secondary display, never the user's main screen.
                    Start with mobile_observe or mobile_open_app, use coordinates from the latest screenshot,
                    observe again after actions when the result is uncertain, and call mobile_stop when done.
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
                        Part(text = "Secondary display screenshot (${response["width"]}x${response["height"]}), following $name ${response["status"]}:"),
                        Part(inlineData = Blob(mimeType = "image/jpeg", displayName = "mobile-use.jpg", data = jpeg)),
                    ),
                ),
            )
        }
    }

    private fun MobileUseResult.asToolResponse(): Map<String, Any> = buildMap {
        put("status", status)
        put("message", message)
        displayId?.let { put("displayId", it) }
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        imageJpeg?.let { bytes ->
            val token = UUID.randomUUID().toString()
            synchronized(pendingImages) { pendingImages[token] = bytes }
            put("imageToken", token)
            put("imageStatus", "Screenshot attached to the next model request only.")
        }
    }

    private fun Map<String, Any?>.int(key: String): Int? = (get(key) as? Number)?.toInt()
    private fun string(description: String) = Schema(type = Type.STRING, description = description)
    private fun integer(description: String) = Schema(type = Type.INTEGER, description = description)
    private fun invalid(message: String, status: String = "invalid_argument") = mapOf("status" to status, "message" to message)
}
