package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentPathManifestTest {

    @Test
    fun `returns null when there is nothing to describe`() {
        assertNull(attachmentPathManifest(emptyList()))
    }

    @Test
    fun `omits attachments that have no persisted path`() {
        val inline = FileAttachment.fromBytes(
            mimeType = "image/png",
            data = byteArrayOf(1, 2, 3),
            displayName = "chart.png",
        )

        assertNull(attachmentPathManifest(listOf(inline)))
    }

    @Test
    fun `lists each persisted attachment with its display name and path`() {
        val manifest = attachmentPathManifest(
            listOf(
                attachment("photo.jpg", "/files/chat-attachments/session/aaa.jpg"),
                attachment("note.pdf", "/files/chat-attachments/session/bbb.pdf"),
            ),
        )

        assertEquals(
            """
            <attachments>
            The user attached 2 file(s). Tools that take a local file path can use these paths as-is.
            1. photo.jpg — /files/chat-attachments/session/aaa.jpg
            2. note.pdf — /files/chat-attachments/session/bbb.pdf
            </attachments>
            """.trimIndent(),
            manifest,
        )
    }

    @Test
    fun `falls back to the bare path when an attachment has no display name`() {
        val manifest = attachmentPathManifest(
            listOf(attachment("", "/files/chat-attachments/session/aaa.jpg")),
        )

        assertEquals(
            """
            <attachments>
            The user attached 1 file(s). Tools that take a local file path can use these paths as-is.
            1. /files/chat-attachments/session/aaa.jpg
            </attachments>
            """.trimIndent(),
            manifest,
        )
    }

    private fun attachment(displayName: String, reference: String) = FileAttachment(
        mimeType = "application/pdf",
        id = reference.substringAfterLast('/').substringBefore('.'),
        sizeBytes = 3,
        displayName = displayName,
        payloadReference = reference,
        category = AttachmentCategory.DOCUMENT,
    )
}
