package github.ponyhuang.gimi.domain.workfiles.usecase

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkDirectoryUseCasesTest {

    @Test
    fun `observe exposes the current list from the repository`() = runTest {
        val state = MutableStateFlow(listOf(directory("uri-1"), directory("uri-2")))
        val repository = FakeRepository(state)
        val observed = ObserveWorkDirectoriesUseCase(repository).invoke().first()

        assertEquals(listOf("uri-1", "uri-2"), observed.map { it.treeUri })
    }

    @Test
    fun `add delegates to repository and returns success`() = runTest {
        val repository = FakeRepository(MutableStateFlow(emptyList()))
        val result = AddWorkDirectoryUseCase(repository).invoke("uri-new")

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertTrue(repository.added.contains("uri-new"))
    }

    @Test
    fun `remove delegates to repository`() = runTest {
        val repository = FakeRepository(MutableStateFlow(listOf(directory("uri-1"))))
        val result = RemoveWorkDirectoryUseCase(repository).invoke("id-uri-1")

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertTrue(repository.removed.contains("id-uri-1"))
    }

    @Test
    fun `set enabled delegates stable id and state`() = runTest {
        val repository = FakeRepository(MutableStateFlow(listOf(directory("uri-1"))))

        val result = SetWorkDirectoryEnabledUseCase(repository)("id-uri-1", false)

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertEquals("id-uri-1" to false, repository.enabledChanges.single())
    }

    @Test
    fun `refresh access delegates to repository`() = runTest {
        val repository = FakeRepository(MutableStateFlow(emptyList()))

        RefreshWorkDirectoryAccessUseCase(repository)()

        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `reauthorize delegates stable id and replacement uri`() = runTest {
        val repository = FakeRepository(MutableStateFlow(listOf(directory("uri-1"))))

        val result = ReauthorizeWorkDirectoryUseCase(repository)("id-uri-1", "uri-replacement")

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertEquals("id-uri-1" to "uri-replacement", repository.reauthorizations.single())
    }

    @Test
    fun `repository returns failure when adding an invalid uri`() = runTest {
        val repository = FakeRepository(MutableStateFlow(emptyList()))
        val result = AddWorkDirectoryUseCase(repository).invoke("not-a-uri")

        assertEquals(WorkDirectoryOperationResult.Failure.InvalidDirectory, result)
        assertFalse(repository.added.contains("not-a-uri"))
    }

    private fun directory(uri: String) = WorkDirectory(
        id = "id-$uri",
        treeUri = uri,
        displayName = uri,
        authority = "com.android.externalstorage.documents",
        enabled = true,
        accessStatus = WorkDirectoryAccessStatus.AVAILABLE,
        addedAtEpochMillis = 1L,
    )

    private class FakeRepository(
        initial: MutableStateFlow<List<WorkDirectory>>,
    ) : WorkDirectoryRepository {
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val enabledChanges = mutableListOf<Pair<String, Boolean>>()
        val reauthorizations = mutableListOf<Pair<String, String>>()
        var refreshCount = 0
        private val state = initial.asStateFlow()

        override fun observeDirectories(): Flow<List<WorkDirectory>> = state

        override suspend fun addDirectory(uri: String): WorkDirectoryOperationResult {
            return if (uri.contains("not-a-uri")) {
                WorkDirectoryOperationResult.Failure.InvalidDirectory
            } else {
                added += uri
                WorkDirectoryOperationResult.Success
            }
        }

        override suspend fun removeDirectory(id: String): WorkDirectoryOperationResult {
            removed += id
            return WorkDirectoryOperationResult.Success
        }

        override suspend fun setEnabled(id: String, enabled: Boolean): WorkDirectoryOperationResult {
            enabledChanges += id to enabled
            return WorkDirectoryOperationResult.Success
        }

        override suspend fun reauthorize(
            id: String,
            uri: String,
        ): WorkDirectoryOperationResult {
            reauthorizations += id to uri
            return WorkDirectoryOperationResult.Success
        }

        override suspend fun refreshAccess() {
            refreshCount++
        }

    }
}
