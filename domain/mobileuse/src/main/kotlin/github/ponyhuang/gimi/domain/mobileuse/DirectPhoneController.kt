package github.ponyhuang.gimi.domain.mobileuse

/**
 * 直控手机（主屏）操作后端契约：与副屏 [MobileUseController] 并行的第二种手机控制通道。
 *
 * 一次委派任务的完整执行（观察、决策、操作、收尾）由实现方自主完成；契约只约定
 * 输入与终局结果，不拆分单步动作。owner 标识一次委派执行，由调用方按「每次委派唯一」
 * 生成并在结束后传入 [finishExecution] 释放；它只作用于本控制器，与副屏
 * [MobileUseController] 的回合 owner 生命周期相互独立，不得复用同一值注册/释放，
 * 也不得在此调用副屏的 registerExecution/finishExecution。sessionId 标识发起委派的
 * 聊天会话，仅用于归因；缺失时调用方按 unavailable 拒绝，不进入后端。
 */
interface DirectPhoneController {

    /**
     * 后端当前可用性诊断；真实执行未就绪时返回 [MobileUseAvailability.UNSUPPORTED]，
     * 装配层据此摘除委派目标。必须廉价、无副作用（会参与 Agent 运行时缓存键计算）。
     */
    fun availability(): MobileUseAvailability

    /**
     * 执行一次直控任务；[instruction] 由子代理从会话中最近一条带文本的用户消息提取——
     * ADK `transfer_to_agent` 只接受 `agent_name`，不传递委派方整理后的指令。
     * 挂起直至任务到达终局（完成或失败）；协程取消即中止执行，不要求再回报结果。
     * 失败应返回 [DirectPhoneResult.STATUS_FAILED] 等结构化结果；异常由调用方统一降级为
     * 同一轮内可解释的失败，不得穿透到聊天级错误处理。
     */
    suspend fun execute(owner: String, sessionId: String, instruction: String): DirectPhoneResult

    /** 释放本次执行权与后端持有的资源；界面状态是否保留由后端决定。 */
    suspend fun finishExecution(owner: String)
}

/**
 * 一次直控任务的终局结果，供委派方模型在同一轮内继续处理。
 *
 * @property status 机器可判定结果码，取值见 companion 常量。
 * @property message 面向委派方模型的处理建议（英文）；失败或不可用时说明回退路径。
 */
data class DirectPhoneResult(
    val status: String,
    val message: String,
) {
    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_UNAVAILABLE = "unavailable"
        const val STATUS_NOT_IMPLEMENTED = "not_implemented"

        /** 委派事件中没有任何可执行任务；由子代理本地判定，不进入后端。 */
        const val STATUS_EMPTY_TASK = "empty_task"
    }
}
