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
import github.ponyhuang.gimi.domain.mobileuse.MobileUseController
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import github.ponyhuang.gimi.domain.mobileuse.MobileKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 与屏幕实现无关的手机工具集；执行归属由运行元数据提供，截图仅注入下一次模型请求。 */
@Singleton
class MobileUseTools @Inject constructor(private val repository: MobileUseController) : Toolset {
    private val pendingImages = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 8
    }

    private val tools = listOf(
        action("mobile_open_app", "Open an installed app by exact display name or package name. Ambiguous names return candidates.",
            mapOf("app_name" to string("Exact app name or package name from list_apps.")), listOf("app_name")) { owner, args ->
            val name = (args["app_name"] as? String)?.trim().orEmpty()
            if (name.isBlank()) invalid("app_name must not be blank.") else repository.openApp(owner, name)
        },
        action("mobile_click", "Click a coordinate on the latest screenshot. Coordinates are integers from 0 to 999.",
            pointProperties(), listOf("x", "y")) { owner, args ->
            val (x, y) = args.point() ?: return@action invalid("x and y must be integers from 0 to 999.")
            repository.click(owner, x, y)
        },
        action("mobile_list_apps", "List launchable apps on this device, returning display names and package names.") { owner, _ ->
            repository.listApps(owner)
        },
        action("mobile_wait", "Wait without capturing or changing the screen. Use take_screenshot afterward to inspect progress. Do not poll long downloads or installs with repeated waits.",
            mapOf("seconds" to integer("Seconds to wait, 1..60; default 1."))) { owner, args ->
            val seconds = args.intOrDefault("seconds", 1)?.takeIf { it in 1..60 }
                ?: return@action invalid("seconds must be an integer from 1 to 60.")
            repository.wait(owner, seconds)
        },
        action("mobile_go_back", "Navigate back once on the controlled screen.") { owner, _ -> repository.goBack(owner) },
        action("mobile_type", "Insert text directly at the current focused field's cursor or selection. This does not use the soft keyboard and will not make one appear. No focus means rejection; does not guess a field, paste through the clipboard or replace unrelated text.",
            mapOf("text" to string("Text to insert."), "press_enter" to Schema(type = Type.BOOLEAN,
                description = "Send Enter only after confirmed text insertion; default false.")), listOf("text")) { owner, args ->
            val text = args["text"] as? String ?: return@action invalid("text must be a string.")
            val enter = if ("press_enter" in args) args["press_enter"] as? Boolean
                ?: return@action invalid("press_enter must be a boolean.") else false
            repository.type(owner, text, enter)
        },
        action("mobile_drag_and_drop", "Move with one continuous touch. Default hold_ms=0 scrolls or swipes; use hold_ms such as 600 to long-press before dragging an item.",
            pointProperties("start_") + pointProperties("end_") + mapOf("hold_ms" to integer("Hold at the start before moving, 0..5000 ms; default 0.")),
            listOf("start_x", "start_y", "end_x", "end_y")) { owner, args ->
            val (startX, startY) = args.point("start_") ?: return@action invalid("Start coordinates must be integers from 0 to 999.")
            val (endX, endY) = args.point("end_") ?: return@action invalid("End coordinates must be integers from 0 to 999.")
            val hold = args.intOrDefault("hold_ms", 0)?.takeIf { it in 0..5000 }
                ?: return@action invalid("hold_ms must be an integer from 0 to 5000.")
            repository.dragAndDrop(owner, startX, startY, endX, endY, hold)
        },
        action("mobile_long_press", "Press and hold a coordinate, then release it.",
            pointProperties() + mapOf("seconds" to integer("Hold duration, 1..5 seconds; default 2.")), listOf("x", "y")) { owner, args ->
            val (x, y) = args.point() ?: return@action invalid("x and y must be integers from 0 to 999.")
            val seconds = args.intOrDefault("seconds", 2)?.takeIf { it in 1..5 }
                ?: return@action invalid("seconds must be an integer from 1 to 5.")
            repository.longPress(owner, x, y, seconds)
        },
        action("mobile_press_key", "Press and release a supported symbolic key on the controlled screen.",
            mapOf("key" to string("Supported keys: ${MobileKey.entries.joinToString { it.name }}.")), listOf("key")) { owner, args ->
            val name = (args["key"] as? String)?.trim()?.uppercase(java.util.Locale.ROOT)?.removePrefix("KEYCODE_")
            val key = MobileKey.entries.firstOrNull { it.name == name }
                ?: return@action invalid("Unsupported key; use one of the declared symbolic keys.")
            repository.pressKey(owner, key)
        },
        action("mobile_take_screenshot", "Capture the current screen quickly, including a changing screen. Use after waiting or when an action's result is uncertain; does not repeat the action.") { owner, _ ->
            repository.takeScreenshot(owner)
        },
        action("mobile_run_shell", "Fallback for an operation the structured mobile tools cannot perform. Runs one privileged Android shell command only with an existing display session. GIMI_DISPLAY_ID contains the controlled display ID; use it explicitly for display-targeted commands. input commands target the controlled display automatically unless the command sets -d/--display, and input text accepts ASCII characters only: use mobile_type for non-ASCII text. Commands can affect the whole device, not only this display. Timeout is 10 seconds; output is limited to 32 KiB. Never automatically retry uncertain actions.",
            mapOf("command" to string("Android shell command, 1..4000 characters. Prefer structured mobile tools; use this only as a fallback.")),
            listOf("command")) { owner, args ->
            val command = args["command"] as? String ?: return@action invalid("command must be a string.")
            if (command.isBlank() || command.length > 4_000 || '\u0000' in command) {
                return@action invalid("command must contain 1..4000 characters without NUL.")
            }
            repository.runShell(owner, command)
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
                    They control the screen provided by the current mobile backend. The user may also interact
                    with it; never assume a previous field or page remains active after user input.
                    Start with mobile_open_app using an exact app name or package name; use mobile_list_apps
                    to resolve unknown or ambiguous names. Never guess between duplicate app names.
                    All coordinates use the full screenshot normalized to integers 0..999 on each axis.
                    Read coordinates from the latest screenshot every time; never reuse coordinates
                    remembered from an earlier screen.
                    Inspect each screenshot, including overlays, quantities and totals, before continuing.
                    Actions that change the screen automatically return a screenshot when capture succeeds.
                    mobile_take_screenshot checks the current state; mobile_wait only waits and defaults to 1 second.
                    A changing screen can still accept actions when the target itself is stable. Carousel banners,
                    ads, video playback and clocks are unrelated animations: do not wait for the whole screen
                    to become settled and do not repeat mobile_wait for them. Wait only for a known loading state,
                    download, installation or a target that is itself moving.
                    Long downloads and installations are multi-minute work: confirm the download started,
                    then report its size, the required "install unknown apps" consent and the chosen store,
                    and hand the remaining wait back to the user instead of polling with mobile_wait.
                    For China-mainland apps prefer TapTap, the vendor store or an official APK before
                    assuming Google Play carries them, and resolve store names with search tools using
                    exact names instead of near synonyms.
                    Observation validity and execution identity are managed internally; do not pass observationId.
                    If stale_observation is returned, inspect a new screenshot before choosing another action.
                    Never repeat an identical failed tool call a third time: after two consecutive failures
                    of the same call with the same arguments, change approach (re-locate the target, switch
                    to another input path) or report the blocker to the user.
                    If actionStatus is delivered or unknown, do not repeat an action because its screenshot
                    failed or timed out. Call mobile_take_screenshot instead. Quiet pixels do not prove
                    business loading is complete; unrelated animations do not require the user to pause playback.
                    mobile_type inserts at the current focused field's cursor or selection. First click the
                    intended field if needed. It does not automatically focus a guessed field or retry input.
                    If text_focus_unavailable is returned even though you already tapped the intended field,
                    the tapped element is probably a fake input such as a rotating hot-search placeholder bar
                    that only opens suggestions instead of taking focus. Do not tap the same coordinates or
                    retype again: verify with one screenshot, open the app's real search or input page, switch
                    to another input path, or report the blocker to the user instead of looping.
                    text_entry_unavailable means two consecutive attempts found no editable focus on this
                    input path: text entry is impossible there. Never call mobile_type or chase focus with
                    keys again. Continue tap-only (browse lists, categories or suggestions and tap the target
                    directly), ask the user to type on the controlled screen, or report the blocker.
                    accessibility_required means the Gimi Text Input accessibility service is off: every
                    mobile_type fails until it is enabled. Tell the user to enable it in system Accessibility
                    settings and stop typing attempts; a screenshot or retry cannot fix it.
                    mobile_type supports Chinese and other non-ASCII text. Shell "input text" is ASCII-only
                    and cannot enter Chinese: to search a Chinese name inside an app, tap the search field,
                    type a short ASCII prefix such as a brand or pinyin ("QQ"), then tap the app's own
                    suggestion or history row instead of typing Chinese.
                    The soft keyboard is intentionally absent on the isolated display and is not proof that
                    input failed. Do not wait for a keyboard, open one, or use clipboard paste as a fallback.
                    A visible cursor is sufficient: call mobile_type directly. Set press_enter=true when the
                    inserted text must be submitted, or use mobile_press_key with ENTER afterward.
                    If text is visibly present or text_typed is returned, do not type it again.
                    Text insertion with a cursor error is partial success: inspect it, never retype automatically.
                    mobile_drag_and_drop with hold_ms=0 performs a swipe for scrolling; set hold_ms when
                    an item needs a long press before dragging. mobile_long_press releases after holding.
                    Prefer the structured mobile tools. mobile_run_shell is an explicit fallback after opening
                    a display session; commands are device-wide shell operations, not a display sandbox.
                    Use ${'$'}GIMI_DISPLAY_ID explicitly for commands that accept a display ID. Never use this
                    fallback to repeat an action with unknown delivery, bypass session closure or handle credentials.
                    For login, passwords, verification codes and sensitive confirmation steps, ask the user
                    to complete them directly on the controlled screen. Do not request passwords or verification
                    codes in chat or tool arguments. Observe again afterward and honor the user's stopping
                    point and consent for submitting, paying or other sensitive actions.
                    Execution lifecycle is managed internally. If session_closed is returned, do not reopen
                    during this execution. If authorization or device support is unavailable, explain the
                    setup returned by the tool instead of retrying the same action.
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
            parameters = Schema(type = Type.OBJECT,
                properties = properties + ("intent" to string("Brief purpose of this action.")), required = required + "intent"),
        )

        override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
            val owner = ToolRunMetadata.mobileUseOwner(context.context.runConfig?.customMetadata)
                ?: return invalid("Agent task identity unavailable.", "unavailable")
            if ((args["intent"] as? String).isNullOrBlank()) return invalid("intent must describe the action purpose.")
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
                        Part(text = "Screenshot (${response["width"]}x${response["height"]}); coordinates 0..999, state=${response["observationStatus"]}, frameAgeMs=${response["frameAgeMs"]}; actionStatus=${response["actionStatus"]}:"),
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
        shellResult?.let { shell ->
            shell.exitCode?.let { put("exitCode", it) }
            put("output", shell.output)
            put("timedOut", shell.timedOut)
            put("outputTruncated", shell.outputTruncated)
        }
        width?.let { put("width", it) }
        height?.let { put("height", it) }
        observation?.let { snapshot ->
            put("observationStatus", snapshot.state)
            put("observationReason", snapshot.reason)
            snapshot.frameAgeMs?.let { put("frameAgeMs", it) }
            snapshot.frameSequence?.let { put("frameSequence", it) }
        }
        put("coordinateSystem", "normalized_0_999")
        if (apps.isNotEmpty() || status == "apps_listed" || status == "ambiguous_app") {
            put("apps", apps.map { mapOf("name" to it.name, "packageName" to it.packageName) })
        }
        imageJpeg?.let { bytes ->
            val token = UUID.randomUUID().toString()
            synchronized(pendingImages) { pendingImages[token] = bytes }
            put("imageToken", token)
            put("imageStatus", "Screenshot attached to the next model request only.")
        }
    }

    private fun pointProperties(prefix: String = "") = mapOf(
        "${prefix}x" to integer("Horizontal coordinate, 0..999 from left to right."),
        "${prefix}y" to integer("Vertical coordinate, 0..999 from top to bottom."),
    )
    private fun Map<String, Any?>.point(prefix: String = ""): Pair<Int, Int>? {
        val x = int("${prefix}x")?.takeIf { it in 0..999 } ?: return null
        val y = int("${prefix}y")?.takeIf { it in 0..999 } ?: return null
        return x to y
    }
    private fun Map<String, Any?>.intOrDefault(key: String, default: Int): Int? = if (key in this) int(key) else default
    private fun Map<String, Any?>.int(key: String): Int? = (get(key) as? Number)?.toDouble()
        ?.takeIf { it.isFinite() && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
    private fun string(description: String) = Schema(type = Type.STRING, description = description)
    private fun integer(description: String) = Schema(type = Type.INTEGER, description = description)
    private fun invalid(message: String, status: String = "invalid_argument") = mapOf("status" to status, "message" to message, "actionStatus" to "not_sent")
}
