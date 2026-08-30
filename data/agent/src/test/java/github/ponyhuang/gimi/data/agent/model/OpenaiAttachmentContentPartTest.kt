package github.ponyhuang.gimi.data.agent.model

import com.openai.client.OpenAIClient
import com.openai.core.jsonMapper
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class OpenaiAttachmentContentPartTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val subject = TestOpenai()

    @Test
    fun `wav and mp3 include the required input audio format`() {
        val wav = json(subject.inlinePart("audio/wav", byteArrayOf(1), "voice.wav"))
        val mp3 = json(subject.inlinePart("audio/mpeg", byteArrayOf(2), "voice.mp3"))

        assertTrue(wav.contains("\"format\":\"wav\""))
        assertTrue(mp3.contains("\"format\":\"mp3\""))
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

        assertTrue(part.contains("\"filename\":\"report.pdf\""))
        assertTrue(part.contains("\"file_data\":\"data:application/pdf;base64,"))
    }

    @Test
    fun `managed local reference is read and serialized inline without a file id`() {
        val file = temporaryFolder.newFile("report.pdf").apply {
            writeBytes("pdf".toByteArray(StandardCharsets.UTF_8))
        }

        val part = json(
            subject.filePart(
                mimeType = "application/pdf",
                displayName = "report.pdf",
                reference = file.absolutePath,
            ),
        )

        assertTrue(part.contains("\"filename\":\"report.pdf\""))
        assertTrue(part.contains("\"file_data\":\"data:application/pdf;base64,"))
        assertTrue(!part.contains("\"file_id\""))
    }

    private fun json(value: Any?): String = jsonMapper().writeValueAsString(value)

    private class TestOpenai : Openai("test", mockk<OpenAIClient>(relaxed = true)) {
        fun inlinePart(mimeType: String, data: ByteArray, displayName: String) =
            buildInlineContentPart(mimeType, data, displayName)

        fun filePart(mimeType: String, displayName: String, reference: String) =
            buildFilePart(mimeType, displayName, reference)
    }
}
