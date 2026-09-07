package github.ponyhuang.gimi.data.workfiles.repository

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import github.ponyhuang.gimi.domain.workfiles.model.WorkFileSearchResult
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DefaultDocumentSearchRepositoryTest {
    @Test
    fun searchUsesOnlyEnabledDirectoriesWithHealthyReadAccess() = runTest {
        val gateway = mockk<DocumentSearchGateway>()
        val healthy = directory("healthy")
        val disabled = directory("disabled", enabled = false)
        val lost = directory("lost", status = WorkDirectoryAccessStatus.PERMISSION_LOST)
        coEvery { gateway.search(healthy.treeUri, "plan") } returns listOf(result("one", 1L))
        val repository = repository(listOf(healthy, disabled, lost), gateway)

        assertEquals(listOf(result("one", 1L)), repository.search("plan"))
        io.mockk.coVerify(exactly = 0) { gateway.search(disabled.treeUri, any()) }
        io.mockk.coVerify(exactly = 0) { gateway.search(lost.treeUri, any()) }
    }

    @Test
    fun searchDeduplicatesByUriSortsNewestAndAppliesLimit() = runTest {
        val gateway = mockk<DocumentSearchGateway>()
        val first = directory("first")
        val second = directory("second")
        coEvery { gateway.search(first.treeUri, "plan") } returns listOf(
            result("duplicate", 1L),
            result("older", 2L),
        )
        coEvery { gateway.search(second.treeUri, "plan") } returns listOf(
            result("duplicate", 3L),
            result("newest", 4L),
        )

        val results = repository(listOf(first, second), gateway).search("plan", limit = 2)

        assertEquals(listOf("newest", "duplicate"), results.map { it.displayName })
    }

    @Test
    fun searchPropagatesCancellation() = runTest {
        val gateway = mockk<DocumentSearchGateway>()
        val directory = directory("root")
        coEvery { gateway.search(any(), any()) } throws CancellationException("cancelled")

        try {
            repository(listOf(directory), gateway).search("plan")
            fail("CancellationException expected")
        } catch (_: CancellationException) {
            // Cancellation is part of the repository contract and must not be converted to empty results.
        }
    }

    @Test
    fun authorizationChecksOnlyConfiguredHealthyTrees() = runTest {
        val gateway = mockk<DocumentSearchGateway>()
        val healthy = directory("healthy")
        val disabled = directory("disabled", enabled = false)
        coEvery { gateway.contains(healthy.treeUri, "content://file/one") } returns true
        val repository = repository(listOf(healthy, disabled), gateway)

        assertEquals(true, repository.isAuthorized("content://file/one"))
        io.mockk.coVerify(exactly = 0) { gateway.contains(disabled.treeUri, any()) }
    }

    private fun repository(
        directories: List<WorkDirectory>,
        gateway: DocumentSearchGateway,
    ) = DefaultDocumentSearchRepository(
        directoryRepository = mockk<WorkDirectoryRepository> {
            every { observeDirectories() } returns flowOf(directories)
        },
        gateway = gateway,
    )

    private fun directory(
        id: String,
        enabled: Boolean = true,
        status: WorkDirectoryAccessStatus = WorkDirectoryAccessStatus.AVAILABLE,
    ) = WorkDirectory(
        id = id,
        treeUri = "content://documents/tree/$id",
        displayName = id,
        authority = "documents",
        enabled = enabled,
        accessStatus = status,
        addedAtEpochMillis = 1L,
    )

    private fun result(name: String, modified: Long) = WorkFileSearchResult(
        contentUri = "content://documents/document/$name",
        displayName = name,
        mimeType = "text/plain",
        sizeBytes = 1L,
        modifiedTimeMillis = modified,
    )
}
