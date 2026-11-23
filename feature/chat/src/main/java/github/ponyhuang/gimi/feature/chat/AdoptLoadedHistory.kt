package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.Message

/**
 * 收尾历史刷新的「身份保持式采用」：把落盘权威历史按位置对齐到当前内存消息上。
 *
 * 流式轮的消息在运行期持有合成/乐观身份（chatbot 的 `$runId:assistant`、发送时随机生成的用户消息
 * id），而 ADK 落盘事件的投影身份与此不同。若收尾时整体替换为落盘历史，`turnId`（用户消息 id）与
 * `message.id` 全部变化，LazyColumn 整轮 item key 随之重建，已完成的回答被迫从流式状态切到静态
 * 路径整段重 parse —— 气泡「刷新式」闪一下。
 *
 * 因此：渲染内容等价的消息保留内存对象身份，只同步收尾标志位与落盘时间戳；从首个分歧点起以落盘
 * 历史为准，保证 SDK 重试替换 partial、外部写入新轮次等真实变化仍然被采用。
 *
 * @param current 当前内存消息（含流式/乐观身份）。
 * @param loaded  落盘历史投影（ADK 事件 id 身份）。
 */
internal fun adoptLoadedHistory(
    current: List<Message>,
    loaded: List<Message>,
): List<Message> {
    // 落盘历史为空 = 会话内容确已被清空（如 discard），与整体替换语义保持一致。
    if (current.isEmpty() || loaded.isEmpty()) return loaded
    val result = ArrayList<Message>(loaded.size)
    var paired = 0
    for (authoritative in loaded) {
        val kept = current.getOrNull(paired)?.takeIf { isSameRenderedContent(it, authoritative) }
        if (kept == null) {
            // 首个分歧点起不再配对：剩余位置整体采用落盘历史。
            result += authoritative
            paired = current.size
        } else {
            // 内容等价：保留内存身份（item key / TextPart.id / channel 订阅稳定）。
            result += kept.copy(
                partial = authoritative.partial,
                turnComplete = authoritative.turnComplete,
                timestamp = authoritative.timestamp,
            )
            paired++
        }
    }
    return result
}

/**
 * 渲染内容等价判定：忽略派生身份（id/invocationId/时间戳）与运行期噪声（argsSummary 展示口径、
 * 附件档案 id），比较决定展示与列表身份的字段。
 */
private fun isSameRenderedContent(memory: Message, persisted: Message): Boolean {
    if (memory.role != persisted.role || memory.author != persisted.author) return false
    if (memory.error != persisted.error || memory.webCitations != persisted.webCitations) return false
    if (memory.isNativeRealtimeTranscript != persisted.isNativeRealtimeTranscript) return false
    if (memory.subtaskBatch?.id != persisted.subtaskBatch?.id) return false
    if (!partsEquivalent(memory.textParts, persisted.textParts) { it.thought to it.text }) return false
    if (!partsEquivalent(memory.functionCalls, persisted.functionCalls) {
            Triple(it.id, it.name, it.confirmationOriginalCallId)
        }) return false
    if (!partsEquivalent(memory.functionResponses, persisted.functionResponses) { it.id to it.name }) {
        return false
    }
    if (memory.fileAttachments.size != persisted.fileAttachments.size) return false
    return memory.fileAttachments.zip(persisted.fileAttachments).all { (live, persisted) ->
        live.payloadReference == persisted.payloadReference &&
            live.fileId == persisted.fileId &&
            live.fileVendor == persisted.fileVendor &&
            live.mimeType == persisted.mimeType &&
            live.displayName == persisted.displayName &&
            live.isMissing == persisted.isMissing
    }
}

/** 按下标逐位比较派生键；长度不同即不等价。 */
private inline fun <T, K> partsEquivalent(
    memory: List<T>,
    persisted: List<T>,
    keyOf: (T) -> K,
): Boolean {
    if (memory.size != persisted.size) return false
    for (index in memory.indices) {
        if (keyOf(memory[index]) != keyOf(persisted[index])) return false
    }
    return true
}
