package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionCallView
import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import github.ponyhuang.gimi.domain.conversation.model.RemoteImageReference
import github.ponyhuang.gimi.domain.conversation.model.RemoteImageResult
import github.ponyhuang.gimi.domain.conversation.model.TextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageVisibilityTest {

    @Test
    fun `remote image results stay visible when tool activity is hidden`() {
        val message = assistantMessage(
            functionResponses = listOf(
                FunctionResponseView(
                    id = "images-1",
                    name = "image_tool",
                    remoteImageResult = RemoteImageResult(
                        images = listOf(RemoteImageReference("https://example.com/photo")),
                    ),
                ),
            ),
        )

        assertTrue(message.isVisibleInChat(showToolActivity = false))
    }

    @Test
    fun `local file search results stay visible when tool activity is hidden`() {
        val message = assistantMessage(
            functionResponses = listOf(
                FunctionResponseView(
                    id = "search-1",
                    name = "search_media_files",
                    localFileSearchResult = LocalFileSearchResult(
                        query = "photo",
                        files = listOf(
                            LocalFileReference(
                                displayName = "photo.jpg",
                                mimeType = "image/jpeg",
                                sizeBytes = 1L,
                                modifiedTimeMillis = 2L,
                                category = "image",
                                contentUri = "content://media/photo",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(message.isVisibleInChat(showToolActivity = false))
    }
    @Test
    fun hidesToolOnlyMessageWhenToolActivityIsDisabled() {
        val message = assistantMessage(
            functionCalls = listOf(FunctionCallView("call", "search_media_files", "()")),
        )

        assertFalse(message.isVisibleInChat(showToolActivity = false))
        assertTrue(message.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun keepsTextMessageWhenToolActivityIsDisabled() {
        val message = assistantMessage(
            textParts = listOf(TextPart(text = "已找到文件")),
            functionCalls = listOf(FunctionCallView("call", "search_media_files", "()")),
        )

        assertTrue(message.isVisibleInChat(showToolActivity = false))
    }

    @Test
    fun hidesConfirmationOnlyMessageEvenWhenToolActivityIsEnabled() {
        val message = assistantMessage(
            functionCalls = listOf(FunctionCallView("confirm-1", ConfirmationToolName, "")),
        )

        assertTrue(message.visibleFunctionCalls().isEmpty())
        assertFalse(message.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun hidesToolSearchProtocolMessagesEvenWhenToolActivityIsEnabled() {
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("search-1", ToolSearchProtocolName, "{}")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("search-1", ToolSearchProtocolName)),
        )

        assertTrue(call.visibleFunctionCalls().isEmpty())
        assertTrue(response.visibleFunctionResponses().isEmpty())
        assertFalse(call.isVisibleInChat(showToolActivity = true))
        assertFalse(response.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun keepsRealToolCallsWhileFilteringConfirmation() {
        val message = assistantMessage(
            functionCalls = listOf(
                FunctionCallView("call", "search_media_files", "()"),
                FunctionCallView("confirm-1", ConfirmationToolName, ""),
            ),
        )

        assertTrue(message.visibleFunctionCalls().map { it.name } == listOf("search_media_files"))
        assertTrue(message.isVisibleInChat(showToolActivity = true))
    }

    @Test
    fun foldsResponseOnlyMessageIntoPreviousAssistantMessage() {
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("c1", "maps_geo", "(address=\"白云山\")")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(call, response).foldToolResponses()

        assertEquals(1, folded.size)
        assertEquals(listOf("c1"), folded[0].functionCalls.map { it.id })
        assertEquals(listOf("c1"), folded[0].functionResponses.map { it.id })
    }

    @Test
    fun deduplicatesResponsesDeliveredTwiceByConfirmationFlow() {
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("c1", "maps_geo", "()")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(call, response, response).foldToolResponses()

        assertEquals(1, folded.size)
        assertEquals(1, folded[0].functionResponses.size)
    }

    @Test
    fun confirmationPlaceholderResponseIsReplacedByRealLocalFileResult() {
        // 确认流程下占位响应与真实结果共用同一 call id；占位先到后，真实文件结果必须替换它，
        // 否则本地文件轮播永远不渲染（真机复现的"搜到文件却不出图"根因）。
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("c1", "search_media_files", "(query=\"screen\")")),
        )
        val placeholder = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "search_media_files")),
        )
        val realResult = assistantMessage(
            functionResponses = listOf(
                FunctionResponseView(
                    id = "c1",
                    name = "search_media_files",
                    localFileSearchResult = LocalFileSearchResult(
                        query = "screen",
                        files = listOf(
                            LocalFileReference(
                                displayName = "screen.png",
                                mimeType = "image/png",
                                sizeBytes = 1L,
                                modifiedTimeMillis = 2L,
                                category = "image",
                                contentUri = "content://media/screen",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val folded = listOf(call, placeholder, realResult).foldToolResponses()

        assertEquals(1, folded.size)
        val responses = folded[0].functionResponses
        assertEquals(1, responses.size)
        assertEquals("screen.png", responses.single().localFileSearchResult?.files?.single()?.displayName)
    }

    @Test
    fun realResultKeepsPlaceholderDroppedEvenWhenArrivingInSameMessage() {
        // 占位与真实结果落在同一条响应消息里（合并事件场景）时同样只保留真实结果。
        val call = assistantMessage(
            functionCalls = listOf(FunctionCallView("c1", "search_media_files", "()")),
        )
        val combined = assistantMessage(
            functionResponses = listOf(
                FunctionResponseView("c1", "search_media_files"),
                FunctionResponseView(
                    id = "c1",
                    name = "search_media_files",
                    remoteImageResult = RemoteImageResult(
                        images = listOf(RemoteImageReference("https://example.com/a.png")),
                    ),
                ),
            ),
        )

        val folded = listOf(call, combined).foldToolResponses()

        assertEquals(1, folded.size)
        val responses = folded[0].functionResponses
        assertEquals(1, responses.size)
        assertEquals("https://example.com/a.png", responses.single().remoteImageResult?.images?.single()?.url)
    }

    @Test
    fun keepsResponseOnlyMessageWhenPreviousMessageIsNotAssistant() {
        val userMessage = Message(
            author = "user",
            role = MessageRole.User,
            textParts = listOf(TextPart(text = "当前位置")),
        )
        val response = assistantMessage(
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(userMessage, response).foldToolResponses()

        assertEquals(2, folded.size)
    }

    @Test
    fun doesNotFoldMessagesThatCarryText() {
        val first = assistantMessage(textParts = listOf(TextPart(text = "先查一下")))
        val second = assistantMessage(
            textParts = listOf(TextPart(text = "结果如下")),
            functionResponses = listOf(FunctionResponseView("c1", "maps_geo")),
        )

        val folded = listOf(first, second).foldToolResponses()

        assertEquals(2, folded.size)
    }

    @Test
    fun findLocalFileSearchResultPrefersNonEmptyFilesOverPlaceholder() {
        // “查看全部”页按 responseId 回查结果：占位响应与真实结果同 id，必须优先取非空列表。
        val placeholder = assistantMessage(
            functionResponses = listOf(
                FunctionResponseView("c1", "search_media_files", localFileSearchResult = LocalFileSearchResult(query = "screen", files = emptyList())),
            ),
        )
        val realResult = assistantMessage(
            functionResponses = listOf(
                FunctionResponseView(
                    id = "c1",
                    name = "search_media_files",
                    localFileSearchResult = LocalFileSearchResult(
                        query = "screen",
                        files = listOf(
                            LocalFileReference(
                                displayName = "screen.png",
                                mimeType = "image/png",
                                sizeBytes = 1L,
                                modifiedTimeMillis = 2L,
                                category = "image",
                                contentUri = "content://media/screen",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val found = findLocalFileSearchResult(listOf(placeholder, realResult), "c1")
        assertEquals("screen.png", found?.files?.single()?.displayName)
        // 只有占位时也不能崩，返回空列表结果。
        assertEquals(0, findLocalFileSearchResult(listOf(placeholder), "c1")?.files?.size)
    }

    private fun assistantMessage(
        textParts: List<TextPart> = emptyList(),
        functionCalls: List<FunctionCallView> = emptyList(),
        functionResponses: List<FunctionResponseView> = emptyList(),
    ) = Message(
        author = "assistant",
        role = MessageRole.Assistant,
        textParts = textParts,
        functionCalls = functionCalls,
        functionResponses = functionResponses,
    )
}
