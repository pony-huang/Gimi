@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.modelaccess

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.LiveConnection
import com.google.adk.kt.annotations.ExperimentalLiveApi
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import java.nio.charset.StandardCharsets
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentResolvingModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @OptIn(ExperimentalLiveApi::class)
    @Test
    fun `live connect resolves attachments and forwards the original connection`() = runTest {
        val file = temporaryFolder.newFile("live.txt").apply { writeText("live") }
        val connection = mockk<LiveConnection>()
        val delegate = RecordingModel(connection)
        val request = LlmRequest(contents = listOf(Content(parts = listOf(Part(
            fileData = FileData(mimeType = "text/plain", fileUri = file.absolutePath),
        )))))
        assertSame(connection, AttachmentResolvingModel(delegate).connect(request))
        assertArrayEquals("live".toByteArray(), delegate.request?.contents?.single()?.parts?.single()?.inlineData?.data)
        assertNotNull(request.contents.single().parts.single().fileData)
    }

    @Test
    fun `local file data is resolved only in delegated request`() = runTest {
        val file = temporaryFolder.newFile("report.pdf").apply {
            writeBytes("pdf".toByteArray(StandardCharsets.UTF_8))
        }
        val originalPart = Part(
            fileData = FileData(
                mimeType = "application/pdf",
                displayName = "report.pdf",
                fileUri = file.absolutePath,
            ),
        )
        val request = LlmRequest(contents = listOf(Content(parts = listOf(originalPart))))
        val delegate = RecordingModel()

        AttachmentResolvingModel(delegate).generateContent(request, stream = false).toList()

        val resolved = delegate.request!!.contents.single().parts.single()
        assertNull(resolved.fileData)
        assertNotNull(resolved.inlineData)
        assertEquals("application/pdf", resolved.inlineData?.mimeType)
        assertEquals("report.pdf", resolved.inlineData?.displayName)
        assertArrayEquals("pdf".toByteArray(StandardCharsets.UTF_8), resolved.inlineData?.data)
        assertEquals(originalPart, request.contents.single().parts.single())
    }

    @Test
    fun `remote file data remains a reference`() = runTest {
        val remote = FileData(
            mimeType = "image/png",
            displayName = "remote.png",
            fileUri = "https://example.com/remote.png",
        )
        val delegate = RecordingModel()

        AttachmentResolvingModel(delegate).generateContent(
            LlmRequest(contents = listOf(Content(parts = listOf(Part(fileData = remote))))),
            stream = false,
        ).toList()

        assertEquals(remote, delegate.request!!.contents.single().parts.single().fileData)
        assertNull(delegate.request!!.contents.single().parts.single().inlineData)
    }

    @Test
    fun `file uri is resolved as local payload`() = runTest {
        val file = temporaryFolder.newFile("photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val delegate = RecordingModel()

        AttachmentResolvingModel(delegate).generateContent(
            LlmRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(
                                fileData = FileData(
                                    mimeType = "image/png",
                                    displayName = "photo.png",
                                    fileUri = file.toURI().toString(),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            stream = false,
        ).toList()

        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            delegate.request!!.contents.single().parts.single().inlineData?.data,
        )
    }

    @Test
    fun `missing local file degrades to text marker and request still proceeds`() = runTest {
        val delegate = RecordingModel()

        AttachmentResolvingModel(delegate).generateContent(
            LlmRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(
                                fileData = FileData(
                                    mimeType = "application/pdf",
                                    displayName = "missing.pdf",
                                    fileUri = temporaryFolder.root.resolve("missing.pdf").absolutePath,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            stream = false,
        ).toList()

        val resolved = delegate.request!!.contents.single().parts.single()
        assertNull(resolved.fileData)
        assertNull(resolved.inlineData)
        assertTrue(resolved.text!!.contains("missing.pdf"))
    }

    @Test
    fun `file data without URI degrades to text marker`() = runTest {
        val delegate = RecordingModel()

        AttachmentResolvingModel(delegate).generateContent(
            LlmRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(
                                fileData = FileData(
                                    mimeType = "application/pdf",
                                    displayName = "broken.pdf",
                                    fileUri = null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            stream = false,
        ).toList()

        val resolved = delegate.request!!.contents.single().parts.single()
        assertNull(resolved.fileData)
        assertTrue(resolved.text!!.contains("broken.pdf"))
    }

    private class RecordingModel(private val connection: LiveConnection? = null) : Model {
        override val name: String = "recording"
        var request: LlmRequest? = null

        @OptIn(ExperimentalLiveApi::class)
        override suspend fun connect(request: LlmRequest): LiveConnection {
            this.request = request
            return checkNotNull(connection)
        }

        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
            this.request = request
            return flowOf(LlmResponse())
        }
    }
}
