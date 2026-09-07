package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Context
import android.net.Uri
import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesToolDocumentSearchTest {
    @Test
    fun searchDocumentsDelegatesToDomainRepositoryAndPreservesStructuredOutput() = runTest {
        val repository = mockk<DocumentSearchRepository>()
        coEvery { repository.search("计划", 50) } returns listOf(
            WorkFileSearchResult(
                contentUri = "content://documents/document/plan",
                displayName = "计划.md",
                mimeType = "text/markdown",
                sizeBytes = 12L,
                modifiedTimeMillis = 34L,
            ),
        )
        val tool = FilesTool(mockk(relaxed = true), mockk(relaxed = true), repository)

        val response = tool.searchDocuments(" 计划 ")

        assertEquals(true, response["success"])
        val result = (response["results"] as List<*>).single() as Map<*, *>
        assertEquals("计划.md", result["displayName"])
        assertEquals("content://documents/document/plan", result["contentUri"])
        coVerify(exactly = 1) { repository.search("计划", 50) }
    }

    @Test
    fun openLocalFileUsesRepositoryAuthorizationForDocumentUri() = runTest {
        mockkStatic(Uri::class)
        val parsedUri = mockk<Uri> {
            io.mockk.every { scheme } returns "content"
            io.mockk.every { authority } returns "documents"
        }
        io.mockk.every { Uri.parse("content://documents/document/plan") } returns parsedUri
        val repository = mockk<DocumentSearchRepository>()
        coEvery { repository.isAuthorized("content://documents/document/plan") } returns false
        val tool = FilesTool(mockk<Context>(relaxed = true), mockk(relaxed = true), repository)

        try {
            val response = tool.openLocalFile("content://documents/document/plan")

            assertEquals(false, response["success"])
            coVerify { repository.isAuthorized("content://documents/document/plan") }
        } finally {
            unmockkStatic(Uri::class)
        }
    }
}
