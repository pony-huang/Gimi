package github.ponyhuang.gimi.domain.workfiles.usecase

import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
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

        assertEquals(listOf("uri-1", "uri-2"), observed.map { it.uri })
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
        val result = RemoveWorkDirectoryUseCase(repository).invoke("uri-1")

        assertEquals(WorkDirectoryOperationResult.Success, result)
        assertTrue(repository.removed.contains("uri-1"))
    }

    @Test
    fun `repository returns failure when adding an invalid uri`() = runTest {
        val repository = FakeRepository(MutableStateFlow(emptyList()))
        val result = AddWorkDirectoryUseCase(repository).invoke("not-a-uri")

        assertEquals(WorkDirectoryOperationResult.Failure.InvalidDirectory, result)
        assertFalse(repository.added.contains("not-a-uri"))
    }

    private fun directory(uri: String) = WorkDirectory(
        uri = uri,
        displayName = uri,
        authority = "com.android.externalstorage.documents",
    )

    private class FakeRepository(
        initial: MutableStateFlow<List<WorkDirectory>>,
    ) : WorkDirectoryRepository {
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        private val state = initial.asStateFlow()

        override fun observeDirectories(): Flow<List<WorkDirectory>> = state

        override fun currentDirectories(): List<WorkDirectory> = state.value

        override suspend fun addDirectory(uri: String): WorkDirectoryOperationResult {
            return if (uri.contains("not-a-uri")) {
                WorkDirectoryOperationResult.Failure.InvalidDirectory
            } else {
                added += uri
                WorkDirectoryOperationResult.Success
            }
        }

        override suspend fun removeDirectory(uri: String): WorkDirectoryOperationResult {
            removed += uri
            return WorkDirectoryOperationResult.Success
        }

        override fun contains(uri: String): Boolean = state.value.any { it.uri == uri }
    }
}