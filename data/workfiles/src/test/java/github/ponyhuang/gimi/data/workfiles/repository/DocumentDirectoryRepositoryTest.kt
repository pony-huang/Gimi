package github.ponyhuang.gimi.data.workfiles.repository

import androidx.datastore.core.DataStoreFactory
import app.cash.turbine.test
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentDirectoryRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun addDirectoryTakesReadOnlyGrantAndUsesProviderDisplayName() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.gateway.inspect(TREE_URI) } returns DocumentTreeInfo(
            treeUri = TREE_URI,
            displayName = "项目资料",
            authority = "documents",
        )

        assertEquals(WorkDirectoryOperationResult.Success, fixture.repository.addDirectory(TREE_URI))

        fixture.repository.observeDirectories().test {
            val directory = awaitItem().single()
            assertEquals("项目资料", directory.displayName)
            assertEquals(WorkDirectoryAccessStatus.AVAILABLE, directory.accessStatus)
            cancelAndIgnoreRemainingEvents()
        }
        verify(exactly = 1) { fixture.gateway.takeReadPermission(TREE_URI) }
    }

    @Test
    fun addDirectoryRejectsDuplicateWithoutTakingAnotherGrant() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.gateway.inspect(TREE_URI) } returns info(TREE_URI)
        fixture.repository.addDirectory(TREE_URI)

        val result = fixture.repository.addDirectory(TREE_URI)

        assertEquals(WorkDirectoryOperationResult.Failure.DuplicateDirectory, result)
        verify(exactly = 1) { fixture.gateway.takeReadPermission(TREE_URI) }
    }

    @Test
    fun addDirectoryRejectsParentOrChildOverlap() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.gateway.inspect(TREE_URI) } returns info(TREE_URI)
        every { fixture.gateway.inspect(CHILD_TREE_URI) } returns info(CHILD_TREE_URI)
        fixture.repository.addDirectory(TREE_URI)
        every {
            fixture.gateway.relationship(TREE_URI, CHILD_TREE_URI)
        } returns DocumentTreeRelationship.OVERLAPPING

        val result = fixture.repository.addDirectory(CHILD_TREE_URI)

        val failure = result as WorkDirectoryOperationResult.Failure.OverlappingDirectory
        assertEquals(fixture.current().single().id, failure.conflictingDirectoryId)
        verify(exactly = 0) { fixture.gateway.takeReadPermission(CHILD_TREE_URI) }
    }

    @Test
    fun refreshAccessPersistsPermissionAndProviderFailures() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.gateway.inspect(TREE_URI) } returns info(TREE_URI)
        every { fixture.gateway.inspect(CHILD_TREE_URI) } returns info(CHILD_TREE_URI)
        fixture.repository.addDirectory(TREE_URI)
        fixture.repository.addDirectory(CHILD_TREE_URI)
        every { fixture.gateway.accessStatus(TREE_URI) } returns
            WorkDirectoryAccessStatus.PERMISSION_LOST
        every { fixture.gateway.accessStatus(CHILD_TREE_URI) } returns
            WorkDirectoryAccessStatus.PROVIDER_UNAVAILABLE

        fixture.repository.refreshAccess()

        assertEquals(
            listOf(
                WorkDirectoryAccessStatus.PERMISSION_LOST,
                WorkDirectoryAccessStatus.PROVIDER_UNAVAILABLE,
            ),
            fixture.current().map { it.accessStatus },
        )
    }

    @Test
    fun setEnabledAndRemoveUseStableId() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.gateway.inspect(TREE_URI) } returns info(TREE_URI)
        fixture.repository.addDirectory(TREE_URI)
        val id = fixture.current().single().id

        assertEquals(WorkDirectoryOperationResult.Success, fixture.repository.setEnabled(id, false))
        assertEquals(false, fixture.current().single().enabled)
        assertEquals(WorkDirectoryOperationResult.Success, fixture.repository.removeDirectory(id))
        assertEquals(emptyList<Any>(), fixture.current())
        verify { fixture.gateway.releaseReadPermission(TREE_URI) }
    }

    @Test
    fun reauthorizePreservesStableIdAndRefreshesProviderMetadata() = runTest {
        val fixture = fixture(backgroundScope)
        every { fixture.gateway.inspect(TREE_URI) } returns info(TREE_URI)
        fixture.repository.addDirectory(TREE_URI)
        val original = fixture.current().single()
        every { fixture.gateway.inspect(CHILD_TREE_URI) } returns DocumentTreeInfo(
            treeUri = CHILD_TREE_URI,
            displayName = "重新授权目录",
            authority = "documents",
        )

        val result = fixture.repository.reauthorize(original.id, CHILD_TREE_URI)

        assertEquals(WorkDirectoryOperationResult.Success, result)
        val updated = fixture.current().single()
        assertEquals(original.id, updated.id)
        assertEquals(CHILD_TREE_URI, updated.treeUri)
        assertEquals("重新授权目录", updated.displayName)
        verify { fixture.gateway.takeReadPermission(CHILD_TREE_URI) }
        verify { fixture.gateway.releaseReadPermission(TREE_URI) }
    }

    private fun fixture(scope: CoroutineScope): Fixture {
        val store = WorkDirectoryConfigStore(
            DataStoreFactory.create(
                serializer = WorkDirectoryConfigSerializer,
                scope = scope,
                produceFile = { File(temporaryFolder.root, "${System.nanoTime()}.json") },
            ),
        )
        val gateway = mockk<DocumentTreeGateway>(relaxed = true) {
            every { takeReadPermission(any()) } returns true
            every { relationship(any(), any()) } returns DocumentTreeRelationship.DISJOINT
            every { accessStatus(any()) } returns WorkDirectoryAccessStatus.AVAILABLE
        }
        return Fixture(DocumentDirectoryRepository(store, gateway), store, gateway)
    }

    private fun info(uri: String) = DocumentTreeInfo(uri, uri.substringAfterLast('/'), "documents")

    /** Test dependencies for one repository instance. */
    private data class Fixture(
        val repository: DocumentDirectoryRepository,
        val store: WorkDirectoryConfigStore,
        val gateway: DocumentTreeGateway,
    ) {
        suspend fun current() = store.current()
    }

    private companion object {
        const val TREE_URI = "content://documents/tree/root"
        const val CHILD_TREE_URI = "content://documents/tree/root%2Fchild"
    }
}
