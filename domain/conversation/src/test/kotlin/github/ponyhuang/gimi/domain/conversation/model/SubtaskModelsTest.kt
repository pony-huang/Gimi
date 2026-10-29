package github.ponyhuang.gimi.domain.conversation.model

import github.ponyhuang.gimi.domain.conversation.usecase.ChatRunEventMapper
import org.junit.Assert.*
import org.junit.Test

/** 冷启动状态与领域事件映射决定历史是否把中断任务误报为成功。 */
class SubtaskModelsTest {
    @Test
    fun coldHistoryOnlyInterruptsUnfinishedTasksAndProgressNeverEndsParentTurn() {
        val batch = SubtaskBatch("batch", "session", "inv", "call", 10, listOf(
            SubtaskRun("a", "完成", "model", SubtaskStatus.COMPLETED, result = "result"),
            SubtaskRun("b", "运行", "model", SubtaskStatus.RUNNING),
            SubtaskRun("c", "排队", "model", SubtaskStatus.QUEUED),
        ))
        assertEquals(listOf(SubtaskStatus.COMPLETED, SubtaskStatus.INTERRUPTED, SubtaskStatus.INTERRUPTED),
            batch.interrupted().tasks.map { it.status })
        val event = ChatRunEvent("progress", "inv", "subtasks", emptyList(), emptyList(), emptyList(),
            false, false, null, null, 10, batch)
        val message = requireNotNull(ChatRunEventMapper.fromEvent(event))
        assertEquals(batch, message.subtaskBatch)
        assertFalse(message.turnComplete)
        assertTrue(message.textParts.isEmpty())
    }
}
