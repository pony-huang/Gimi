package github.ponyhuang.gimi.data.mobileuse

/** 记录一次工具调用中的动作投递；异常代表结果未知，绝不能据此自动再次发送动作。 */
internal class MobileActionDelivery(action: Boolean) {
    var status: String = if (action) "not_sent" else "not_requested"
        private set
    var completedAtMs: Long? = null
        private set

    fun attempt(nowMs: () -> Long, invalidate: () -> Unit, send: () -> Boolean): Boolean {
        invalidate()
        val alreadyDelivered = status == "delivered"
        status = "unknown"
        val accepted = send()
        status = if (accepted || alreadyDelivered) "delivered" else "rejected"
        if (accepted) completedAtMs = nowMs()
        return accepted
    }
}
