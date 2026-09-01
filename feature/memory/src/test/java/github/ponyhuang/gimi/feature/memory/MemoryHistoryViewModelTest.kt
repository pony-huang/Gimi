package github.ponyhuang.gimi.feature.memory

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.memory.model.ManagedMemory
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryFeedback
import github.ponyhuang.gimi.domain.memory.model.ManagedMemoryPage
import github.ponyhuang.gimi.domain.memory.repository.Mem0MemoryManagementRepository
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads the initial cloud memory page`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecordingMemoryManagementRepository(
            pages = listOf(ManagedMemoryPage(listOf(memory("m1")), hasNextPage = true)),
        )

        val viewModel = MemoryHistoryViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf("m1"), viewModel.uiState.value.memories.map { it.id })
        assertTrue(viewModel.uiState.value.hasNextPage)
        assertEquals(listOf(1), repository.requestedPages)
    }

    @Test
    fun `deleting a confirmed memory removes it from the visible list`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecordingMemoryManagementRepository(
            pages = listOf(ManagedMemoryPage(listOf(memory("m1")), hasNextPage = false)),
        )
        val viewModel = MemoryHistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(MemoryHistoryAction.RequestDelete(memory("m1")))
        viewModel.onAction(MemoryHistoryAction.ConfirmDelete)
        advanceUntilIdle()

        assertEquals(listOf("m1"), repository.deletedIds)
        assertTrue(viewModel.uiState.value.memories.isEmpty())
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `submitting negative feedback forwards a nonblank reason`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecordingMemoryManagementRepository(
            pages = listOf(ManagedMemoryPage(listOf(memory("m1")), hasNextPage = false)),
        )
        val viewModel = MemoryHistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(MemoryHistoryAction.SubmitFeedback(memory("m1"), ManagedMemoryFeedback.NEGATIVE, "Not correct"))
        advanceUntilIdle()

        assertEquals(listOf(Triple("m1", ManagedMemoryFeedback.NEGATIVE, "Not correct")), repository.feedbacks)
    }

    @Test
    fun `tapping a memory toggles its expanded actions`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecordingMemoryManagementRepository(
            pages = listOf(ManagedMemoryPage(listOf(memory("m1")), hasNextPage = false)),
        )
        val viewModel = MemoryHistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(MemoryHistoryAction.ToggleExpanded("m1"))
        assertEquals(setOf("m1"), viewModel.uiState.value.expandedMemoryIds)

        viewModel.onAction(MemoryHistoryAction.ToggleExpanded("m1"))
        assertTrue(viewModel.uiState.value.expandedMemoryIds.isEmpty())
    }

    @Test
    fun `loading an overlapping page keeps every visible memory id unique`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = RecordingMemoryManagementRepository(
            pages = listOf(
                ManagedMemoryPage(listOf(memory("m1"), memory("m2")), hasNextPage = true),
                ManagedMemoryPage(listOf(memory("m2"), memory("m3")), hasNextPage = false),
            ),
        )
        val viewModel = MemoryHistoryViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(MemoryHistoryAction.LoadNextPage)
        advanceUntilIdle()

        assertEquals(listOf("m1", "m2", "m3"), viewModel.uiState.value.memories.map { it.id })
    }

    private fun memory(id: String) = ManagedMemory(
        id = id,
        text = "Likes tea",
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-01T01:00:00Z"),
    )
}

private class RecordingMemoryManagementRepository(
    private val pages: List<ManagedMemoryPage>,
) : Mem0MemoryManagementRepository {
    val requestedPages = mutableListOf<Int>()
    val deletedIds = mutableListOf<String>()
    val feedbacks = mutableListOf<Triple<String, ManagedMemoryFeedback, String?>>()

    override suspend fun loadPage(page: Int, pageSize: Int): ManagedMemoryPage {
        requestedPages += page
        return pages.getOrElse(page - 1) { ManagedMemoryPage(emptyList(), hasNextPage = false) }
    }

    override suspend fun delete(memoryId: String) {
        deletedIds += memoryId
    }

    override suspend fun submitFeedback(memoryId: String, feedback: ManagedMemoryFeedback, reason: String?) {
        feedbacks += Triple(memoryId, feedback, reason)
    }
}
