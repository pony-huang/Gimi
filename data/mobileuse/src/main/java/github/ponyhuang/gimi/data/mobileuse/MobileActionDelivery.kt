package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult

/** 记录一次工具调用中的动作投递；异常代表结果未知，绝不能据此自动再次发送动作。 */
internal class MobileActionDelivery(action: Boolean) {
    var status: String = if (action) MobileUseResult.ACTION_NOT_SENT else MobileUseResult.ACTION_NOT_REQUESTED
        private set
    var completedAtMs: Long? = null
        private set

    fun attempt(nowMs: () -> Long, invalidate: () -> Unit, send: () -> Boolean): Boolean {
        invalidate()
        val alreadyDelivered = status == MobileUseResult.ACTION_DELIVERED
        status = MobileUseResult.ACTION_UNKNOWN
        val accepted = send()
        status = if (accepted || alreadyDelivered) MobileUseResult.ACTION_DELIVERED else MobileUseResult.ACTION_REJECTED
        if (accepted) completedAtMs = nowMs()
        return accepted
    }
}
