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

    private fun blocksOf(
        request: LlmRequest,
        messageIndex: Int,
        encoding: InputAudioDataEncoding = InputAudioDataEncoding.OPENAI_BASE64,
    ): JsonArray {
        val converted = request.toChatCompletionRequest("deepseek-chat", encoding)
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
                            mimeType = "video/mp4",
                            displayName = "clip.mp4",
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
        assertTrue(placeholder.content.contains("clip.mp4"))
        assertEquals("continue", ((blocks[1] as JsonObject)["text"] as JsonPrimitive).content)
    }

    @Test
    fun inlineAudioConvertsToInputAudioBlock() {
        val request = request(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        inlineData = Blob(
                            mimeType = "audio/wav",
                            data = byteArrayOf(1, 2, 3),
                        ),
                    ),
                ),
            ),
        )
        val blocks = blocksOf(request, 0)
        val audioBlock = blocks.single() as JsonObject
        assertEquals("input_audio", (audioBlock["type"] as JsonPrimitive).content)
        val inputAudio = audioBlock["input_audio"] as JsonObject
        assertEquals("wav", (inputAudio["format"] as JsonPrimitive).content)
        // OpenAI 契约：裸 base64 + format；data-URI 只用于 MiMo 专属适配器。
        assertEquals("AQID", (inputAudio["data"] as JsonPrimitive).content)
    }

    @Test
    fun inlineAudioUsesDataUriWhenMimoEncodingIsRequested() {
        val request = request(
            Content(
                role = Role.USER,
                parts = listOf(
                    Part(
                        inlineData = Blob(
                            mimeType = "audio/wav",
                            data = byteArrayOf(1, 2, 3),
                        ),
                    ),
                ),
            ),
        )
        val blocks = blocksOf(request, 0, InputAudioDataEncoding.MIMO_DATA_URI)
        val inputAudio = (blocks.single() as JsonObject)["input_audio"] as JsonObject
        assertEquals("data:audio/wav;base64,AQID", (inputAudio["data"] as JsonPrimitive).content)
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
