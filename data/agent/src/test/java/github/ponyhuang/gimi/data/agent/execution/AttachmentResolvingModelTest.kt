package github.ponyhuang.gimi.data.agent.execution

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AttachmentResolvingModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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

    @Test(expected = IllegalArgumentException::class)
    fun `missing local file fails before provider invocation`() = runTest {
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
    }

    private class RecordingModel : Model {
        override val name: String = "recording"
        var request: LlmRequest? = null

        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> {
            this.request = request
            return flowOf(LlmResponse())
        }
    }
}
