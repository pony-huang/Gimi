package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 单一 Agent 工具入口，图像只临时注入紧接着的一轮模型请求。 */
@Singleton
class MobileUseTool @Inject constructor(
    private val repository: MobileUseRepository,
) : FunctionTool(
    name = "mobile_use",
    description = "Operate another Android app on an isolated 720x1280 virtual display via Shizuku ADB shell. " +
        "Actions: observe, launch, tap, swipe, back, stop. After every action a screenshot is attached " +
        "to the next model request. Coordinates refer to the virtual display, never the physical screen.",
) {
    private val pendingImages = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > 8
    }

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "action" to Schema(type = Type.STRING, description = "observe | launch | tap | swipe | back | stop"),
                "packageName" to Schema(type = Type.STRING, description = "Android package name for launch"),
                "x" to Schema(type = Type.INTEGER, description = "Tap x coordinate, 0..719"),
                "y" to Schema(type = Type.INTEGER, description = "Tap y coordinate, 0..1279"),
                "x1" to Schema(type = Type.INTEGER, description = "Swipe start x"),
                "y1" to Schema(type = Type.INTEGER, description = "Swipe start y"),
                "x2" to Schema(type = Type.INTEGER, description = "Swipe end x"),
                "y2" to Schema(type = Type.INTEGER, description = "Swipe end y"),
                "durationMs" to Schema(type = Type.INTEGER, description = "Swipe duration, 100..5000 ms"),
            ),
            required = listOf("action"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
        val owner = ToolRunMetadata.mobileUseOwner(context.context.runConfig?.customMetadata)
            ?: return mapOf("status" to "unavailable", "message" to "Agent task identity unavailable.")
        val result = when (args["action"]) {
            "observe" -> repository.observe(owner)
            "launch" -> {
                val packageName = (args["packageName"] as? String)?.trim().orEmpty()
                if (!packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
                    return mapOf("status" to "invalid_argument", "message" to "Valid packageName required.")
                }
                repository.launch(owner, packageName)
            }
            "tap" -> {
                val x = args.int("x") ?: return invalid("x")
                val y = args.int("y") ?: return invalid("y")
                repository.tap(owner, x, y)
            }
            "swipe" -> {
                val x1 = args.int("x1") ?: return invalid("x1")
                val y1 = args.int("y1") ?: return invalid("y1")
                val x2 = args.int("x2") ?: return invalid("x2")
                val y2 = args.int("y2") ?: return invalid("y2")
                repository.swipe(owner, x1, y1, x2, y2, args.int("durationMs") ?: 450)
            }
            "back" -> repository.back(owner)
            "stop" -> repository.stop(owner)
            else -> return mapOf("status" to "invalid_argument", "message" to "Unknown action.")
        }
        return result.asToolResponse()
    }

    private fun MobileUseResult.asToolResponse(): Map<String, Any> = buildMap {
        put("status", status)
        put("message", message)
        displayId?.let { put("displayId", it) }
        imageJpeg?.let { bytes ->
            val token = UUID.randomUUID().toString()
            synchronized(pendingImages) { pendingImages[token] = bytes }
            put("imageToken", token)
            put("width", 720)
            put("height", 1280)
            put("imageStatus", "Screenshot attached to the next model request only.")
        }
    }

    override suspend fun processLlmRequest(
        toolContext: ToolContext,
        llmRequest: LlmRequest,
    ): LlmRequest {
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
                    Part(text = "Virtual display screenshot (720x1280), following mobile_use ${response["status"]}:"),
                    Part(inlineData = Blob(mimeType = "image/jpeg", displayName = "mobile-use.jpg", data = jpeg)),
                ),
            ),
        )
    }

    private fun Map<String, Any?>.int(key: String): Int? = (get(key) as? Number)?.toInt()
    private fun invalid(name: String): Map<String, String> =
        mapOf("status" to "invalid_argument", "message" to "$name must be an integer.")
}
