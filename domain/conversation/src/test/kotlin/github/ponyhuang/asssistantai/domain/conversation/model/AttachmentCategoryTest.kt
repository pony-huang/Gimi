package github.ponyhuang.asssistantai.domain.conversation.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttachmentCategoryTest {
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
    fun `file attachment equality includes metadata and byte content`() {
        val first = FileAttachment(
            id = "id",
            displayName = "report.pdf",
            mimeType = "application/pdf",
            sizeBytes = 3,
            category = AttachmentCategory.DOCUMENT,
            data = byteArrayOf(1, 2, 3),
        )

        assertEquals(first, first.copy(data = byteArrayOf(1, 2, 3)))
        assertNotEquals(first, first.copy(displayName = "other.pdf"))
        assertNotEquals(first, first.copy(data = byteArrayOf(3, 2, 1)))
    }
}
