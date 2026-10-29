package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.Serializable

/** 子任务的可证明状态；中断与取消均不能视为完成。 */
@Serializable
enum class SubtaskStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, INTERRUPTED }

/** 子任务内的工具步骤，只保留函数名与状态，不持久化参数或思考内容。 */
@Serializable
data class SubtaskToolStep(val id: String, val name: String, val completed: Boolean = false)

/**
 * 一次隔离分析任务及其可展示结果。
 * @property id 批次内稳定标识；与父 invocation、委派调用及完整节点路径共同定位执行。
 * @property nodePath SDK 适配层提供的完整节点路径，不能只使用局部 runId。
 * @property result 成功的最终回答，不包含原始思考。
 * @property error 受控错误码，不包含厂商异常或凭据。
 */
@Serializable
data class SubtaskRun(
    val id: String,
    val title: String,
    val modelId: String,
    val status: SubtaskStatus = SubtaskStatus.QUEUED,
    val nodePath: String = "",
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val result: String = "",
    val error: String? = null,
    val tools: List<SubtaskToolStep> = emptyList(),
)

/**
 * 一次委派批次；实时进度与历史重放共用同一不可变快照。
 * @property id 父会话、父 invocation 与工具调用派生的稳定批次 ID。
 * @property invocationId 父聊天的执行 ID，重试保留该关联。
 * @property callId 主助手 delegate_tasks 调用 ID。
 * @property createdAt 批次第一次创建的时间，更新进度不改变时间线位置。
 */
@Serializable
data class SubtaskBatch(
    val id: String,
    val sessionId: String,
    val invocationId: String,
    val callId: String,
    val createdAt: Long,
    val tasks: List<SubtaskRun>,
) {
    /** 冷启动后没有活跃执行的未完成任务应显示为中断，而不是永久运行。 */
    fun interrupted(): SubtaskBatch = copy(tasks = tasks.map {
        if (it.status == SubtaskStatus.QUEUED || it.status == SubtaskStatus.RUNNING) {
            it.copy(status = SubtaskStatus.INTERRUPTED)
        } else it
    })
}
