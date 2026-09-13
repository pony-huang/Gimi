package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.FunctionResponseView
import github.ponyhuang.gimi.domain.conversation.model.LocalFileReference
import github.ponyhuang.gimi.domain.conversation.model.LocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.Message
import github.ponyhuang.gimi.domain.conversation.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSearchRouteTest {
    @Test
    fun `local result lookup prefers non-empty response over placeholder`() {
        val placeholder = responseMessage(LocalFileSearchResult(query = "screen", files = emptyList()))
        val actual = responseMessage(
            LocalFileSearchResult(
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
        )

        assertEquals(
            "screen.png",
            findLocalFileSearchResult(listOf(placeholder, actual), "call-1")
                ?.files?.single()?.displayName,
        )
        assertEquals(0, findLocalFileSearchResult(listOf(placeholder), "call-1")?.files?.size)
    }

    private fun responseMessage(result: LocalFileSearchResult) = Message(
        author = "assistant",
        role = MessageRole.Assistant,
        functionResponses = listOf(
            FunctionResponseView(
                id = "call-1",
                name = "search_media_files",
                localFileSearchResult = result,
            ),
        ),
    )
}
