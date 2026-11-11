package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneController
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneResult
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 直控手机后端占位实现：直控主屏的真实执行（观察、输入注入、任务循环）尚未实装。
 * [availability] 恒为 [MobileUseAvailability.UNSUPPORTED]，装配层据此不挂载委派目标；
 * [execute] 只保留一条结构化回退结果，供单元验证与实装前的契约核对。
 * 实装时替换本类并保持 Hilt 绑定不变，无需改动 agent 装配层。
 */
@Singleton
class PlaceholderDirectPhoneController @Inject constructor() : DirectPhoneController {

    override fun availability(): MobileUseAvailability = MobileUseAvailability.UNSUPPORTED

    override suspend fun execute(owner: String, sessionId: String, instruction: String): DirectPhoneResult =
        DirectPhoneResult(
            status = DirectPhoneResult.STATUS_NOT_IMPLEMENTED,
            message = "Direct phone control is not implemented yet. Do not claim success and do not " +
                "retry the transfer in this invocation. Continue as the main assistant: explain that " +
                "direct phone control is unavailable, then use only tools that are actually exposed " +
                "in this session if they can complete the task.",
        )

    override suspend fun finishExecution(owner: String) = Unit
}
