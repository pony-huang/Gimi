package github.ponyhuang.gimi.domain.conversation.model

/**
 * 模型停止生成的原因（SDK 无关）。
 *
 * 协议适配层把厂商的 stop reason 归一到这四档，domain 不感知任何厂商枚举。
 *
 * @property STOP 正常结束：模型答完了，或转入工具调用。
 * @property MAX_TOKENS 触达输出上限，回答被截断。
 * @property SAFETY 模型拒答或内容被安全策略拦截。
 * @property OTHER 其余无法归类的原因。
 */
enum class ChatFinishReason {
    STOP,
    MAX_TOKENS,
    SAFETY,
    OTHER,
}
