package github.ponyhuang.gimi.data.conversation.mapper

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventMapperAttachmentTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `restores a file backed attachment without loading its bytes`() {
        val id = "c".repeat(64)
        val payload = temporaryFolder.newFile("$id.jpg")
            .apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val event = userEvent(
            Part(
                fileData = FileData(
                    mimeType = "image/jpeg",
                    displayName = "photo.jpg",
                    fileUri = payload.absolutePath,
                ),
            ),
        )

        val attachment = EventMapper.fromEvent(event)?.fileAttachments?.single()

        assertEquals(id, attachment?.id)
        assertEquals(payload.absolutePath, attachment?.payloadReference)
        assertEquals(4L, attachment?.sizeBytes)
        assertNull(attachment?.inlineData)
    }

    @Test
    fun `keeps inline bytes for attachments that have no backing file`() {
        val event = userEvent(
            Part(
                inlineData = Blob(
                    mimeType = "image/png",
                    displayName = "chart.png",
                    data = byteArrayOf(9, 8, 7),
                ),
            ),
        )

        val attachment = EventMapper.fromEvent(event)?.fileAttachments?.single()

        assertNull(attachment?.payloadReference)
        assertArrayEquals(byteArrayOf(9, 8, 7), attachment?.inlineData)
    }

    private fun userEvent(vararg parts: Part): Event = mockk {
        every { id } returns "event-attachment"
        every { invocationId } returns "invocation-attachment"
        every { author } returns "user"
        every { content } returns Content(role = Role.USER, parts = parts.toList())
        every { functionCalls() } returns emptyList()
        every { functionResponses() } returns emptyList()
        every { partial } returns false
        every { turnComplete } returns true
        every { errorCode } returns null
        every { errorMessage } returns null
        every { timestamp } returns 123L
    }
}
