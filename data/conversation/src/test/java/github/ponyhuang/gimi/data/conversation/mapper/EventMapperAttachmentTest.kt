package github.ponyhuang.gimi.data.conversation.mapper

import android.util.Log
import com.google.adk.kt.events.Event
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.adk.kt.sessions.Session
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventMapperAttachmentTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

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
        assertEquals(false, attachment?.isMissing)
        assertNull(attachment?.inlineData)
    }

    @Test
    fun `maps a missing payload file to an isMissing placeholder instead of throwing`() {
        val missing = File(temporaryFolder.root, "gone/missing-payload.jpg")
        val event = userEvent(
            Part(
                fileData = FileData(
                    mimeType = "image/jpeg",
                    displayName = "photo.jpg",
                    fileUri = missing.absolutePath,
                ),
            ),
        )

        val attachment = EventMapper.fromEvent(event)?.fileAttachments?.single()

        assertEquals(true, attachment?.isMissing)
        assertEquals(missing.absolutePath, attachment?.payloadReference)
        assertEquals("missing-payload", attachment?.id)
        assertEquals("photo.jpg", attachment?.displayName)
        assertNull(attachment?.inlineData)
    }

    @Test
    fun `fromSession keeps messages whose attachment files are missing`() {
        val missing = File(temporaryFolder.root, "also-missing.pdf")
        val session = mockk<Session> {
            every { events } returns mutableListOf(
                userEvent(
                    Part(
                        fileData = FileData(
                            mimeType = "application/pdf",
                            displayName = "doc.pdf",
                            fileUri = missing.absolutePath,
                        ),
                    ),
                ),
            )
        }

        val messages = EventMapper.fromSession(session)

        assertEquals(1, messages.size)
        assertEquals(true, messages.single().fileAttachments.single().isMissing)
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

    @Test
    fun `drops inline attachment without payload but keeps the message`() {
        val event = userEvent(
            Part(text = "看一下这个"),
            Part(inlineData = Blob(mimeType = "image/png", displayName = "chart.png", data = null)),
        )

        val message = EventMapper.fromEvent(event)

        assertEquals(0, message?.fileAttachments?.size ?: -1)
        assertEquals("看一下这个", message?.textParts?.single()?.text)
    }

    @Test
    fun `drops file attachment part without mime type instead of throwing`() {
        val event = userEvent(
            Part(
                fileData = FileData(
                    mimeType = null,
                    displayName = "legacy.bin",
                    fileUri = temporaryFolder.newFile("legacy.bin").absolutePath,
                ),
            ),
        )

        val message = EventMapper.fromEvent(event)

        assertEquals(0, message?.fileAttachments?.size ?: -1)
    }

    @Test
    fun `drops attachment with unsupported mime type instead of throwing`() {
        val payload = temporaryFolder.newFile("scan.tiff").apply { writeBytes(byteArrayOf(1)) }
        val event = userEvent(
            Part(
                fileData = FileData(
                    mimeType = "image/tiff",
                    displayName = "scan.tiff",
                    fileUri = payload.absolutePath,
                ),
            ),
        )

        val message = EventMapper.fromEvent(event)

        assertEquals(0, message?.fileAttachments?.size ?: -1)
    }

    @Test
    fun `fromSession keeps every other message when one event is poisonous`() {
        val poison = mockk<Event> {
            every { id } returns "event-poison"
            every { content } throws IllegalStateException("corrupt stored event")
        }
        val session = mockk<Session> {
            every { events } returns mutableListOf(
                userEvent(Part(text = "第一条")),
                poison,
                userEvent(Part(text = "最后一条")),
            )
        }

        val messages = EventMapper.fromSession(session)

        assertEquals(
            listOf("第一条", "最后一条"),
            messages.map { it.textParts.single().text },
        )
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
