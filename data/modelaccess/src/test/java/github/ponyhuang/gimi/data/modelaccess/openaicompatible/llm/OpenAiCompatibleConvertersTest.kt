package github.ponyhuang.gimi.data.modelaccess.openaicompatible.llm

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fork 转换器的新增行为：gimi-file 引用直通为 `file` 块、不支持的媒体降级为占位文本
 * （不再抛 IllegalArgumentException）、历史重放不因坏附件而阻断。
 */
class OpenAiCompatibleConvertersTest {

    private fun request(vararg contents: Content) = LlmRequest(contents = contents.toList())

    private fun blocksOf(request: LlmRequest, messageIndex: Int): JsonArray {
        val converted = request.toChatCompletionRequest("deepseek-chat")
        val content = converted.messages[messageIndex].content
        return content as JsonArray
    }

    @Test
    fun vendorFileReferenceBecomesFileBlock() {
        val request = request(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(text = "总结这份财报"),
                    Part(
                        fileData = FileData(
                            mimeType = "application/pdf",
                            displayName = "doc.pdf",
                            fileUri = "gimi-file://deepseek/file-abc",
                        ),
                    ),
                ),
            ),
        )
        val blocks = blocksOf(request, 0)
        assertEquals(2, blocks.size)
        val fileBlock = blocks[1] as JsonObject
        assertEquals("file", (fileBlock["type"] as JsonPrimitive).content)
        assertEquals("file-abc", (fileBlock["file_id"] as JsonPrimitive).content)
    }

    @Test
    fun localDocumentReferenceDegradesToPlaceholderInsteadOfThrowing() {
        val request = request(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        fileData = FileData(
                            mimeType = "application/pdf",
                            displayName = "doc.pdf",
                            fileUri = "/tmp/local/doc.pdf",
                        ),
                    ),
                ),
            ),
        )
        val blocks = blocksOf(request, 0)
        val placeholder = (blocks.single() as JsonObject)["text"] as JsonPrimitive
        assertTrue("content=${placeholder.content}", placeholder.content.contains("doc.pdf"))
    }

    @Test
    fun unsupportedInlineDataInHistoryDegradesInsteadOfThrowing() {
        val request = request(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        inlineData = Blob(
                            mimeType = "audio/wav",
                            displayName = "clip.wav",
                            data = byteArrayOf(1),
                        ),
                    ),
                    Part(text = "continue"),
                ),
            ),
        )
        val blocks = blocksOf(request, 0)
        assertEquals(2, blocks.size)
        val placeholder = (blocks[0] as JsonObject)["text"] as JsonPrimitive
        assertTrue(placeholder.content.contains("clip.wav"))
        assertEquals("continue", ((blocks[1] as JsonObject)["text"] as JsonPrimitive).content)
    }

    @Test
    fun inlineImageStillConvertsToDataUrl() {
        val request = request(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        inlineData = Blob(
                            mimeType = "image/png",
                            data = byteArrayOf(1, 2, 3),
                        ),
                    ),
                ),
            ),
        )
        val blocks = blocksOf(request, 0)
        val imageBlock = blocks.single() as JsonObject
        assertEquals("image_url", (imageBlock["type"] as JsonPrimitive).content)
    }
}
