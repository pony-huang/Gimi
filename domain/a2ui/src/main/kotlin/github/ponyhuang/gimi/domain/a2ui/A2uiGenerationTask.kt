package github.ponyhuang.gimi.domain.a2ui

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 卡片创建任务的可持久化状态；重试只重做展示生成，不重复业务工具。 */
@Serializable
enum class A2uiGenerationStatus { PENDING, COMPLETED, FAILED, CANCELLED }

/**
 * 一次创建的输入快照和状态，供异步完成关联、历史恢复及原位重试。
 * @property callId 原始工具调用标识。
 * @property surfaceId 创建及重试始终使用的界面标识。
 * @property invocationId 原始轮次标识，重试不创建新的用户轮次。
 * @property changes 主 Agent 已收集的完整展示事实。
 * @property componentNames 已选择的目录组件。
 * @property status 当前生成状态。
 */
@Serializable
data class A2uiGenerationTask(
    val callId: String,
    val surfaceId: String,
    val invocationId: String,
    val changes: String,
    val componentNames: Set<String>,
    val status: A2uiGenerationStatus = A2uiGenerationStatus.PENDING,
)

/** 任务快照采用 JSON 字符串存放，避免把运行对象带入 SDK 的事件 metadata。 */
object A2uiGenerationTaskCodec {
    const val KEY = "a2ui_generation_task"
    const val RESULT_KEY = "a2ui_generation_result"
    const val AUTHOR = "a2ui_generation"

    fun encode(task: A2uiGenerationTask): String = Json.encodeToString(task)

    fun extract(response: Map<String, Any?>): A2uiGenerationTask? {
        val payload = (response["result"] as? Map<*, *>) ?: response
        val raw = payload[KEY] as? String ?: return null
        return try {
            Json.decodeFromString<A2uiGenerationTask>(raw)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
