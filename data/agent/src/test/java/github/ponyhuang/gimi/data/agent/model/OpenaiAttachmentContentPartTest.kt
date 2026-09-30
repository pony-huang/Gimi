package github.ponyhuang.gimi.data.agent.model

import com.openai.client.OpenAIClient
import com.openai.core.jsonMapper
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import java.util.Base64
import org.junit.Test

class OpenaiAttachmentContentPartTest {
    private val subject = TestOpenai()

    @Test
    fun `wav and mp3 include the required input audio format`() {
        val wav = json(subject.inlinePart("audio/wav", byteArrayOf(1), "voice.wav"))
        val mp3 = json(subject.inlinePart("audio/mpeg", byteArrayOf(2), "voice.mp3"))

        assertEquals("input_audio", wav.path("type").asText())
        assertEquals("wav", wav.path("input_audio").path("format").asText())
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(1)), wav.path("input_audio").path("data").asText())
        assertEquals("input_audio", mp3.path("type").asText())
        assertEquals("mp3", mp3.path("input_audio").path("format").asText())
        assertEquals(Base64.getEncoder().encodeToString(byteArrayOf(2)), mp3.path("input_audio").path("data").asText())
    }

    @Test
    fun `document is encoded as inline file data with its original filename`() {
        val part = json(
            subject.inlinePart(
                mimeType = "application/pdf",
                data = "pdf".toByteArray(StandardCharsets.UTF_8),
                displayName = "report.pdf",
            ),
        )

        assertEquals("file", part.path("type").asText())
        assertEquals("report.pdf", part.path("file").path("filename").asText())
        assertEquals("data:application/pdf;base64,cGRm", part.path("file").path("file_data").asText())
    }

    @Test
    fun `remote image reference is serialized as image url`() {
        val part = json(subject.remoteFilePart("image/png", "https://example.com/image.png"))

        assertEquals("image_url", part.path("type").asText())
        assertEquals("https://example.com/image.png", part.path("image_url").path("url").asText())
    }

    private fun json(value: Any?) = jsonMapper().readTree(jsonMapper().writeValueAsString(value))

    private class TestOpenai : Openai("test", mockk<OpenAIClient>(relaxed = true)) {
        fun inlinePart(mimeType: String, data: ByteArray, displayName: String) =
            buildInlineContentPart(mimeType, data, displayName)

        fun remoteFilePart(mimeType: String, reference: String) =
            buildRemoteFilePart(mimeType, reference)
    }
}
