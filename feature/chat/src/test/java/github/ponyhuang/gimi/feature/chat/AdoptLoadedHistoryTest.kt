package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationTask
import github.ponyhuang.gimi.domain.a2ui.A2uiToolResult
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.Messages
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Test

/** 收尾历史刷新的身份保持式采用：内容等价保留内存身份，分歧点起以落盘历史为准。 */
class AdoptLoadedHistoryTest {

    private fun userMessage(
        id: String,
        text: String,
        timestamp: Long = 1_000L,
    ): Message = Messages.fromUser(text).copy(id = id, timestamp = timestamp)

    private fun assistantMessage(
        id: String,
        text: String,
        partial: Boolean = false,
        turnComplete: Boolean = true,
        timestamp: Long = 2_000L,
    ): Message = Messages.fromAssistant(id = id).copy(
        textParts = listOf(TextPart(text = text)),
        partial = partial,
        turnComplete = turnComplete,
        timestamp = timestamp,
    )

    @Test
    fun `equivalent history keeps in-memory identities and only syncs completion flags`() {
        // 流式收尾：内存里是合成 id + partial 标志，落盘历史是 ADK 事件 id + 收尾标志。
        val current = listOf(
            userMessage("optimistic-user", "你好"),
            assistantMessage("run-1:assistant", "回复", partial = true, turnComplete = false),
        )
        val loaded = listOf(
            userMessage("adk-user-event", "你好", timestamp = 1_100L),
            assistantMessage("adk-final-event", "回复", turnComplete = true, timestamp = 2_100L),
        )

        val adopted = adoptLoadedHistory(current, loaded)

        assertEquals(listOf("optimistic-user", "run-1:assistant"), adopted.map { it.id })
        assertEquals(false, adopted[1].partial)
        assertEquals(true, adopted[1].turnComplete)
        // 收尾标志位与时间戳同步为落盘值。
        assertEquals(2_100L, adopted[1].timestamp)
        assertEquals(1_100L, adopted[0].timestamp)
    }

    @Test
    fun `diverged turn adopts persisted history from the mismatch onward`() {
        // SDK 重试替换未保存的 partial：正文不同，分歧位置起采用落盘历史，
        // 分歧之前内容等价的消息仍然保留内存身份（避免无谓的整轮 key 变化）。
        val current = listOf(
            userMessage("optimistic-user", "你好"),
            assistantMessage("run-1:assistant", "旧的部分回复", partial = true),
        )
        val loaded = listOf(
            userMessage("adk-user-event", "你好"),
            assistantMessage("adk-final-event", "重试后的完整回复"),
        )

        val adopted = adoptLoadedHistory(current, loaded)

        assertEquals(listOf("optimistic-user", "adk-final-event"), adopted.map { it.id })
    }

    @Test
    fun `externally written turn is adopted while earlier identities stay stable`() {
        // 语音等外部写入在后台追加新轮次：既有轮次身份保留，新轮次采用落盘身份。
        val current = listOf(
            userMessage("user-1", "第一问"),
            assistantMessage("assistant-1", "第一答"),
        )
        val loaded = listOf(
            userMessage("user-1", "第一问"),
            assistantMessage("assistant-1", "第一答"),
            userMessage("voice-user-2", "第二问"),
            assistantMessage("voice-assistant-2", "第二答"),
        )

        val adopted = adoptLoadedHistory(current, loaded)

        assertEquals(
            listOf("user-1", "assistant-1", "voice-user-2", "voice-assistant-2"),
            adopted.map { it.id },
        )
    }

    @Test
    fun `tool calls pair by id and name ignoring display-only args summary`() {
        val liveCall = github.ponyhuang.gimi.domain.conversation.model.FunctionCallView(
            id = "call-1", name = "web_search", argsSummary = "{query=天气, raw=true}",
        )
        val persistedCall = github.ponyhuang.gimi.domain.conversation.model.FunctionCallView(
            id = "call-1", name = "web_search", argsSummary = "(query=天气)",
        )
        val current = listOf(assistantMessage("run-1:assistant", "答").copy(functionCalls = listOf(liveCall)))
        val loaded = listOf(
            assistantMessage("adk-final-event", "答").copy(functionCalls = listOf(persistedCall)),
        )

        val adopted = adoptLoadedHistory(current, loaded)

        // argsSummary 只是展示口径差异：身份保留，展示随内存对象（与落盘内容一致）。
        assertEquals("run-1:assistant", adopted.single().id)
    }

    @Test
    fun `persisted A2UI result replaces stale placeholder while keeping message identity`() {
        val task = A2uiGenerationTask("call", "surface", "invocation", "fixture", setOf("Text"))
        val pending = FunctionResponseView(task.callId, A2uiToolResult.TOOL_NAME, a2uiTask = task)
        val completed = pending.copy(
            a2uiTask = task.copy(status = A2uiGenerationStatus.COMPLETED),
            a2uiDocument = A2uiDocument(task.surfaceId, listOf("fixture")),
        )
        val live = assistantMessage("live", "").copy(functionResponses = listOf(pending))
        val persisted = assistantMessage("persisted", "").copy(functionResponses = listOf(completed))

        val adopted = adoptLoadedHistory(listOf(live), listOf(persisted)).single()

        assertEquals(live.id, adopted.id)
        assertEquals(listOf(completed), adopted.functionResponses)
    }

    @Test
    fun `attachment identity difference falls back to persisted history`() {
        val live = FileAttachment(
            mimeType = "text/plain", id = "live-id", sizeBytes = 10,
            displayName = "a.txt", payloadReference = "/workspace/a.txt",
        )
        val persisted = live.copy(id = "db-id")
        val current = listOf(userMessage("optimistic-user", "看文件").copy(fileAttachments = listOf(live)))
        val loaded = listOf(userMessage("adk-user-event", "看文件").copy(fileAttachments = listOf(persisted)))

        // 档案 id 只是运行期噪声，payloadReference/mime/名称一致即等价，身份保留。
        val adopted = adoptLoadedHistory(current, loaded)
        assertEquals("optimistic-user", adopted.single().id)
    }

    @Test
    fun `empty inputs fall back to the authoritative list`() {
        assertEquals(emptyList<Message>(), adoptLoadedHistory(emptyList(), emptyList()))
        val loaded = listOf(userMessage("adk-user-event", "你好"))
        assertEquals(loaded, adoptLoadedHistory(emptyList(), loaded))
        val current = listOf(userMessage("optimistic-user", "你好"))
        assertEquals(emptyList<Message>(), adoptLoadedHistory(current, emptyList()))
    }

    @Test
    fun `transcript flag and subtask batch differences adopt persisted history`() {
        val current = listOf(
            userMessage("optimistic-user", "结束前的话")
                .copy(isNativeRealtimeTranscript = false),
        )
        val loaded = listOf(
            userMessage("adk-transcript", "结束前的话").copy(isNativeRealtimeTranscript = true),
        )

        assertEquals(loaded, adoptLoadedHistory(current, loaded))
    }

    @Test
    fun `role mismatch adopts persisted history`() {
        val current = listOf(
            Message(id = "wrong-role", author = "Chatbot", role = MessageRole.Assistant, textParts = listOf(TextPart(text = "你好"))),
        )
        val loaded = listOf(userMessage("adk-user-event", "你好"))

        assertEquals(loaded, adoptLoadedHistory(current, loaded))
    }
}
