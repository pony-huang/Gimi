package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.*
import org.junit.Assert.*
import org.junit.Test

/** 子任务映射属于时间线的行为变化，固定原工具步骤和最终回答不会被替换。 */
class SubtaskTimelineTest {
    @Test
    fun activeTaskTreeDoesNotAppendAnotherEmptyUsingToolPill() {
        val batch = SubtaskBatch("batch", "session", "inv", "call", 0, listOf(
            SubtaskRun("one", "分析", "model", SubtaskStatus.RUNNING),
        ))
        val timeline = TurnTimeline("turn", true, listOf(TurnSegment.Subtasks("tree", batch)))
        assertEquals(timeline.segments, timeline.displaySegments(ChatRunStatus.UsingTool("delegate_tasks")))
    }

    @Test
    fun preservesOrdinaryTimelineAndRendersOneTaskTreeWithoutDuplicatedDelegation() {
        val batch = SubtaskBatch("batch", "session", "inv", "delegate", 120, listOf(
            SubtaskRun("task", "分析资料", "model", SubtaskStatus.COMPLETED, result = "child result"),
        ))
        val messages = listOf(
            Messages.fromUser("分析资料", id = "user", timestamp = 100),
            Messages.fromAssistant(id = "search", timestamp = 110).copy(
                functionCalls = listOf(FunctionCallView("search", "search_documents", "")),
                functionResponses = listOf(FunctionResponseView("search", "search_documents")),
            ),
            Messages.fromAssistant(id = "delegate", timestamp = 120).copy(
                functionCalls = listOf(FunctionCallView("delegate", "delegate_tasks", "")),
            ),
            Messages.fromAssistant(id = "progress", timestamp = 121).copy(subtaskBatch = batch),
            Messages.fromAssistant(id = "response", timestamp = 130).copy(
                functionResponses = listOf(FunctionResponseView("delegate", "delegate_tasks")),
            ),
            Messages.fromAssistant(id = "final", timestamp = 140).copy(textParts = listOf(TextPart(text = "parent answer"))),
        )
        val timeline = (messages.toChatListItems(TimelineActivityState()).last() as ChatListItem.AssistantTurn).timeline
        assertEquals(listOf("search_documents"), timeline.segments.filterIsInstance<TurnSegment.Activity>()
            .flatMap { it.entries }.filterIsInstance<TimelineEntry.ToolCall>().map { it.name })
        assertEquals(batch, timeline.segments.filterIsInstance<TurnSegment.Subtasks>().single().batch)
        assertEquals(listOf("parent answer"), timeline.segments.filterIsInstance<TurnSegment.Answer>()
            .flatMap { it.message.textParts }.map { it.text })
    }
}
