package github.ponyhuang.gimi.domain.conversation.model

/** 聊天入口与通知使用的会话模式，历史与运行状态按此模式隔离。 */
enum class ChatMode { ASSISTANT, CHATBOT }

/** 通知发起的一次会话定位请求，requestId 区分重复点击。 */
data class ChatLaunchRequest(val mode: ChatMode, val sessionId: String?, val requestId: Long) {
    companion object {
        const val EXTRA_MODE = "gimi.chat.mode"
        const val EXTRA_SESSION = "gimi.chat.session"
    }
}
