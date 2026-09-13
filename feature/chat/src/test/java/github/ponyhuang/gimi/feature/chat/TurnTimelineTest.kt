package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTimelineTest {

    @Test
    fun `messages are grouped into stable user and assistant turn items`() {
        val messages = listOf(
            userMessage(id = "user-a", timestamp = 100L),
            assistantMessage(id = "assistant-b", timestamp = 200L, text = "B"),
            assistantMessage(id = "assistant-c", timestamp = 300L, text = "C"),
            userMessage(id = "user-d", timestamp = 400L),
            assistantMessage(id = "assistant-e", timestamp = 500L, text = "E"),
        )

        val items = messages.toChatListItems(TimelineActivityState())

        assertEquals(4, items.size)
        assertEquals("user-a", (items[0] as ChatListItem.UserMessage).message.id)
        val firstTurn = (items[1] as ChatListItem.AssistantTurn).timeline
        assertEquals("user-a", firstTurn.turnId)
        assertEquals(listOf("assistant-b", "assistant-c"), firstTurn.answerMessages.map { it.id })
        assertEquals("user-d", (items[2] as ChatListItem.UserMessage).message.id)
        assertEquals("user-d", (items[3] as ChatListItem.AssistantTurn).timeline.turnId)
    }

    @Test
    fun `running user creates empty assistant placeholder with stable turn id`() {
        val user = userMessage(id = "user-current", timestamp = 1_000L)

        val running = listOf(user).toChatListItems(
            TimelineActivityState(isAgentRunning = true),
        )
        val idle = listOf(user).toChatListItems(TimelineActivityState())

        assertEquals(2, running.size)
        val placeholder = (running[1] as ChatListItem.AssistantTurn).timeline
        assertEquals("user-current", placeholder.turnId)
        assertTrue(placeholder.isRunning)
        assertTrue(placeholder.entries.isEmpty())
        assertTrue(placeholder.answerMessages.isEmpty())
        assertEquals(1, idle.size)
    }

    @Test
    fun `thought tool and file results become timeline entries while protocols stay hidden`() {
        val fileResult = localFileResult("plan.md")
        val assistant = assistantMessage(
            id = "assistant",
            timestamp = 300L,
            textParts = listOf(
                TextPart(id = "thought", text = "先检索", thought = true),
                TextPart(id = "answer", text = "已找到", thought = false),
            ),
            functionCalls = listOf(
                FunctionCallView("call-1", "search_documents", "(query=计划)"),
                FunctionCallView("protocol-1", ConfirmationToolName, ""),
                FunctionCallView("protocol-2", ToolSearchProtocolName, ""),
            ),
            functionResponses = listOf(
                FunctionResponseView("call-1", "search_documents", fileResult),
                FunctionResponseView("protocol-1", ConfirmationToolName),
                FunctionResponseView("protocol-2", ToolSearchProtocolName),
            ),
        )

        val items = listOf(userMessage(timestamp = 100L), assistant).toChatListItems(
            TimelineActivityState(),
        )
        val timeline = (items[1] as ChatListItem.AssistantTurn).timeline

        assertEquals(3, timeline.entries.size)
        assertEquals("thought", (timeline.entries[0] as TimelineEntry.Thought).partId)
        val tool = timeline.entries[1] as TimelineEntry.ToolCall
        assertEquals("call-1", tool.callId)
        assertEquals(ToolCallStatus.Completed, tool.status)
        assertEquals(fileResult, (timeline.entries[2] as TimelineEntry.FileResults).response.localFileSearchResult)
        assertEquals(listOf("assistant"), timeline.answerMessages.map { it.id })
    }

    @Test
    fun `duplicate placeholder response is replaced by structured result`() {
        val call = assistantMessage(
            id = "call-message",
            timestamp = 200L,
            functionCalls = listOf(FunctionCallView("call-1", "search_documents", "()")),
        )
        val placeholder = assistantMessage(
            id = "placeholder",
            timestamp = 300L,
            functionResponses = listOf(FunctionResponseView("call-1", "search_documents")),
        )
        val actual = assistantMessage(
            id = "actual",
            timestamp = 400L,
            functionResponses = listOf(
                FunctionResponseView("call-1", "search_documents", localFileResult("plan.md")),
            ),
        )

        val timeline = (listOf(userMessage(timestamp = 100L), call, placeholder, actual, actual)
            .toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        assertEquals(1, timeline.entries.filterIsInstance<TimelineEntry.ToolCall>().size)
        val files = timeline.entries.filterIsInstance<TimelineEntry.FileResults>()
        assertEquals(1, files.size)
        assertEquals("plan.md", files.single().response.localFileSearchResult?.files?.single()?.displayName)
    }

    @Test
    fun `response-only event completes the preceding call without a second entry`() {
        val call = assistantMessage(
            id = "call",
            timestamp = 200L,
            functionCalls = listOf(FunctionCallView("call-1", "maps_geo", "()")),
        )
        val response = assistantMessage(
            id = "response",
            timestamp = 300L,
            functionResponses = listOf(FunctionResponseView("call-1", "maps_geo")),
        )

        val timeline = (listOf(userMessage(timestamp = 100L), call, response)
            .toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        val tools = timeline.entries.filterIsInstance<TimelineEntry.ToolCall>()
        assertEquals(1, tools.size)
        assertEquals(ToolCallStatus.Completed, tools.single().status)
    }

    @Test
    fun `structured response replaces placeholder in the same event`() {
        val combined = assistantMessage(
            timestamp = 200L,
            functionCalls = listOf(FunctionCallView("call-1", "search_documents", "()")),
            functionResponses = listOf(
                FunctionResponseView("call-1", "search_documents"),
                FunctionResponseView("call-1", "search_documents", localFileResult("plan.md")),
            ),
        )

        val timeline = (listOf(userMessage(timestamp = 100L), combined)
            .toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        val files = timeline.entries.filterIsInstance<TimelineEntry.FileResults>()
        assertEquals(1, files.size)
        assertEquals("plan.md", files.single().response.localFileSearchResult?.files?.single()?.displayName)
    }

    @Test
    fun `timestamps derive from user and final assistant with invalid duration falling back`() {
        val valid = (listOf(
            userMessage(id = "valid", timestamp = 1_000L),
            assistantMessage(timestamp = 2_000L, text = "partial"),
            assistantMessage(timestamp = 4_000L, text = "final"),
        ).toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline
        val invalid = (listOf(
            userMessage(id = "invalid", timestamp = 5_000L),
            assistantMessage(timestamp = 5_000L, text = "answer"),
        ).toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        assertEquals(1_000L, valid.startedAtMs)
        assertEquals(4_000L, valid.finishedAtMs)
        assertEquals(5_000L, invalid.startedAtMs)
        assertNull(invalid.finishedAtMs)
    }

    @Test
    fun `last valid assistant timestamp wins when a later event has no timestamp`() {
        val timeline = (listOf(
            userMessage(timestamp = 1_000L),
            assistantMessage(id = "valid", timestamp = 3_000L, text = "answer"),
            assistantMessage(id = "missing", timestamp = 0L, functionResponses = listOf(
                FunctionResponseView("call-1", "search_documents"),
            )),
        ).toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        assertEquals(3_000L, timeline.finishedAtMs)
    }

    @Test
    fun `tool statuses are isolated by call id and historical missing evidence is unknown`() {
        val assistant = assistantMessage(
            timestamp = 200L,
            functionCalls = listOf(
                FunctionCallView("rejected", "same_tool", "()"),
                FunctionCallView("awaiting", "same_tool", "()"),
                FunctionCallView("unknown", "same_tool", "()"),
            ),
        )
        val activity = TimelineActivityState(
            toolStatuses = mapOf(
                ToolCallKey("rejected", "same_tool") to ToolCallStatus.Rejected,
                ToolCallKey("awaiting", "same_tool") to ToolCallStatus.AwaitingConfirmation,
            ),
        )

        val timeline = (listOf(userMessage(timestamp = 100L), assistant)
            .toChatListItems(activity)[1] as ChatListItem.AssistantTurn).timeline
        val statuses = timeline.entries.filterIsInstance<TimelineEntry.ToolCall>()
            .associate { it.callId to it.status }

        assertEquals(ToolCallStatus.Rejected, statuses["rejected"])
        assertEquals(ToolCallStatus.AwaitingConfirmation, statuses["awaiting"])
        assertEquals(ToolCallStatus.Unknown, statuses["unknown"])
        assertFalse(timeline.isRunning)
    }

    @Test
    fun `explicit running status is not inferred for another call with same name`() {
        val assistant = assistantMessage(
            timestamp = 200L,
            functionCalls = listOf(
                FunctionCallView("running", "same_tool", "()"),
                FunctionCallView("unknown", "same_tool", "()"),
            ),
        )
        val activity = TimelineActivityState(
            isAgentRunning = true,
            toolStatuses = mapOf(
                ToolCallKey("running", "same_tool") to ToolCallStatus.Running,
            ),
        )

        val timeline = (listOf(userMessage(timestamp = 100L), assistant)
            .toChatListItems(activity)[1] as ChatListItem.AssistantTurn).timeline
        val statuses = timeline.entries.filterIsInstance<TimelineEntry.ToolCall>()
            .associate { it.callId to it.status }

        assertTrue(timeline.isRunning)
        assertEquals(ToolCallStatus.Running, statuses["running"])
        assertEquals(ToolCallStatus.Unknown, statuses["unknown"])
    }

    @Test
    fun `historical rejected confirmation is restored by original call id`() {
        val originalCall = assistantMessage(
            id = "call",
            timestamp = 200L,
            functionCalls = listOf(FunctionCallView("original-1", "delete_file", "(path=…)")),
        )
        val confirmationRequest = assistantMessage(
            id = "confirmation-request",
            timestamp = 300L,
            functionCalls = listOf(
                FunctionCallView(
                    id = "confirm-1",
                    name = ConfirmationToolName,
                    argsSummary = "",
                    confirmationOriginalCallId = "original-1",
                    confirmationOriginalToolName = "delete_file",
                ),
            ),
        )
        val confirmationResponse = assistantMessage(
            id = "confirmation-response",
            timestamp = 400L,
            functionResponses = listOf(
                FunctionResponseView(
                    id = "confirm-1",
                    name = ConfirmationToolName,
                    confirmationApproved = false,
                ),
            ),
        )

        val timeline = (listOf(
            userMessage(timestamp = 100L),
            originalCall,
            confirmationRequest,
            confirmationResponse,
        ).toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        assertEquals(
            ToolCallStatus.Rejected,
            timeline.entries.filterIsInstance<TimelineEntry.ToolCall>().single().status,
        )
        assertEquals(1, timeline.entries.size)
    }

    @Test
    fun `historical approval without tool response remains unknown`() {
        val timeline = (listOf(
            userMessage(timestamp = 100L),
            assistantMessage(
                timestamp = 200L,
                functionCalls = listOf(FunctionCallView("original-1", "delete_file", "()")),
            ),
            assistantMessage(
                timestamp = 300L,
                functionCalls = listOf(
                    FunctionCallView(
                        id = "confirm-1",
                        name = ConfirmationToolName,
                        argsSummary = "",
                        confirmationOriginalCallId = "original-1",
                        confirmationOriginalToolName = "delete_file",
                    ),
                ),
                functionResponses = listOf(
                    FunctionResponseView(
                        id = "confirm-1",
                        name = ConfirmationToolName,
                        confirmationApproved = true,
                    ),
                ),
            ),
        ).toChatListItems(TimelineActivityState())[1] as ChatListItem.AssistantTurn).timeline

        assertEquals(
            ToolCallStatus.Unknown,
            timeline.entries.filterIsInstance<TimelineEntry.ToolCall>().single().status,
        )
    }

    private fun userMessage(
        id: String = "user",
        timestamp: Long,
    ) = Message(
        id = id,
        author = "user",
        role = MessageRole.User,
        textParts = listOf(TextPart(text = "question")),
        timestamp = timestamp,
    )

    private fun assistantMessage(
        id: String = "assistant",
        timestamp: Long,
        text: String? = null,
        textParts: List<TextPart> = text?.let { listOf(TextPart(text = it)) }.orEmpty(),
        functionCalls: List<FunctionCallView> = emptyList(),
        functionResponses: List<FunctionResponseView> = emptyList(),
    ) = Message(
        id = id,
        author = "assistant",
        role = MessageRole.Assistant,
        textParts = textParts,
        functionCalls = functionCalls,
        functionResponses = functionResponses,
        timestamp = timestamp,
    )

    private fun localFileResult(displayName: String) = LocalFileSearchResult(
        query = "计划",
        files = listOf(
            LocalFileReference(
                displayName = displayName,
                mimeType = "text/markdown",
                sizeBytes = 1L,
                modifiedTimeMillis = 2L,
                category = "document",
                contentUri = "content://documents/$displayName",
            ),
        ),
    )
}
