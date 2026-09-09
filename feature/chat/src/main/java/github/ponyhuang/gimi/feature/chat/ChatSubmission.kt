package github.ponyhuang.gimi.feature.chat

/** 发送接管结果；ACCEPTED 表示输入已归档且执行上下文就绪，不表示模型回答成功。 */
enum class ChatSubmissionResult { ACCEPTED, REJECTED }

/** 一次发送只交付一次回执，正常结束、准备失败和取消共用同一完成边界。 */
internal class ChatSubmission(private val onResult: (ChatSubmissionResult) -> Unit) {
    var result: ChatSubmissionResult? = null
        private set

    fun complete(result: ChatSubmissionResult) {
        if (this.result != null) return
        this.result = result
        onResult(result)
    }
}

/** 只消费已接管的快照；晚到的回执不能清除其后输入的文字或附件。 */
internal fun consumeAcceptedDraft(current: MessageData, submitted: MessageData): MessageData =
    current.copy(
        text = if (current.text == submitted.text) "" else current.text,
        attachments = current.attachments.filterNot { it in submitted.attachments },
    )
