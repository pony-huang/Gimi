package github.ponyhuang.gimi.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentCategoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `classifies supported image audio and document mime types`() {
        assertEquals(AttachmentCategory.IMAGE, AttachmentCategory.from("image/png", "photo.png"))
        assertEquals(AttachmentCategory.AUDIO, AttachmentCategory.from("audio/mpeg", "clip.mp3"))
        assertEquals(AttachmentCategory.DOCUMENT, AttachmentCategory.from("application/pdf", "paper.pdf"))
        assertEquals(AttachmentCategory.DOCUMENT, AttachmentCategory.from("text/x-kotlin", "Main.kt"))
    }

    @Test
    fun `uses extension when content resolver reports generic mime type`() {
        assertEquals(
            AttachmentCategory.AUDIO,
            AttachmentCategory.from("application/octet-stream", "voice.wav"),
        )
        assertEquals(
            AttachmentCategory.DOCUMENT,
            AttachmentCategory.from(null, "notes.docx"),
        )
    }

    @Test
    fun `rejects videos and unsupported files`() {
        assertNull(AttachmentCategory.from("video/mp4", "movie.mp4"))
        assertNull(AttachmentCategory.from("application/zip", "archive.zip"))
    }

    @Test
    fun `file attachment equality includes metadata and inline bytes`() {
        val first = FileAttachment(
            id = "id",
            displayName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 3,
            category = AttachmentCategory.DOCUMENT,
            inlineData = byteArrayOf(1, 2, 3),
        )

        assertEquals(first, first.copy(inlineData = byteArrayOf(1, 2, 3)))
        assertNotEquals(first, first.copy(displayName = "other.pdf"))
        assertNotEquals(first, first.copy(inlineData = byteArrayOf(3, 2, 1)))
        assertNotEquals(first, first.copy(id = "other"))
    }

    @Test
    fun `reference backed attachments compare on identity not payload`() {
        fun attachment(reference: String) = FileAttachment(
            id = "id",
            displayName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 3,
            category = AttachmentCategory.DOCUMENT,
            payloadReference = reference,
        )

        val first = attachment("/files/chat-attachments/session/id.pdf")

        assertNull(first.inlineData)
        assertEquals(first, attachment("/files/chat-attachments/session/id.pdf"))
        assertNotEquals(first, attachment("/files/chat-attachments/other/id.pdf"))
    }

    @Test
    fun `attachment requires either a reference or inline bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            FileAttachment(
                id = "id",
                displayName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = 3,
                category = AttachmentCategory.DOCUMENT,
            )
        }
    }

    @Test
    fun `fromBytes derives a content addressed id`() {
        val attachment = FileAttachment.fromBytes(
            mimeType = "application/pdf",
            data = byteArrayOf(1, 2, 3),
            displayName = "report.pdf",
        )

        assertEquals(
            attachment.id,
            FileAttachment.fromBytes("application/pdf", byteArrayOf(1, 2, 3), "report.pdf").id,
        )
        assertNotEquals(
            attachment.id,
            FileAttachment.fromBytes("application/pdf", byteArrayOf(3, 2, 1), "report.pdf").id,
        )
        assertEquals(3L, attachment.sizeBytes)
        assertNull(attachment.payloadReference)
    }

    @Test
    fun `fromFile describes the payload without reading it`() {
        val id = "a".repeat(64)
        val file = temporaryFolder.newFile("$id.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val attachment = FileAttachment.fromFile(
            file = file,
            mimeType = "application/pdf",
            displayName = "report.pdf",
        )

        assertEquals(id, attachment.id)
        assertEquals(file.absolutePath, attachment.payloadReference)
        assertEquals(3L, attachment.sizeBytes)
        assertNull(attachment.inlineData)
        assertEquals(AttachmentCategory.DOCUMENT, attachment.category)
    }

    /** Older payloads were stored without an extension; the id must survive either shape. */
    @Test
    fun `fromFile recovers the id from an extensionless payload`() {
        val id = "b".repeat(64)
        val file = temporaryFolder.newFile(id)

        val attachment = FileAttachment.fromFile(
            file = file,
            mimeType = "application/pdf",
            displayName = "report.pdf",
        )

        assertEquals(id, attachment.id)
    }
}
