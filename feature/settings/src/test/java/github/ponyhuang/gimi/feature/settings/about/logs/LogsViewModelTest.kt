package github.ponyhuang.gimi.feature.settings.about.logs

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.logging.LogRepository
import github.ponyhuang.gimi.domain.logging.LogSnapshot
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeRepository : LogRepository {
        var snapshot = LogSnapshot("recent", truncated = true)
        var readFailure: Exception? = null
        var exportFailure: Exception? = null
        var pendingRead: CompletableDeferred<LogSnapshot>? = null
        var reads = 0
        val destinations = mutableListOf<String>()
        override suspend fun read(): LogSnapshot {
            reads++
            readFailure?.let { throw it }
            return pendingRead?.await() ?: snapshot
        }
        override suspend fun export(destination: String) {
            exportFailure?.let { throw it }
            destinations += destination
        }
    }

    @Test fun `opens with loading and displays snapshot`() = runTest(main.dispatcher) {
        val repository = FakeRepository()
        val vm = LogsViewModel(repository)
        vm.open()
        assertTrue(vm.uiState.value.loading)
        advanceUntilIdle()
        assertEquals("recent", vm.uiState.value.content)
        assertTrue(vm.uiState.value.truncated)
        assertFalse(vm.uiState.value.loading)
    }

    @Test fun `close cancels loading and reopening displays fresh logs`() = runTest(main.dispatcher) {
        val repository = FakeRepository()
        val pending = CompletableDeferred<LogSnapshot>()
        repository.pendingRead = pending
        val vm = LogsViewModel(repository)
        vm.open()
        runCurrent()
        vm.close()
        repository.pendingRead = null
        repository.snapshot = LogSnapshot("fresh")
        vm.open()
        pending.complete(LogSnapshot("stale"))
        advanceUntilIdle()
        assertEquals("fresh", vm.uiState.value.content)
        assertTrue(vm.uiState.value.visible)
    }

    @Test fun `read failure shows retry and retry can recover`() = runTest(main.dispatcher) {
        val repository = FakeRepository().apply { readFailure = IOException() }
        val vm = LogsViewModel(repository)
        vm.open()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.failed)
        assertFalse(vm.uiState.value.loading)
        repository.readFailure = null
        vm.open()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.failed)
        assertEquals("recent", vm.uiState.value.content)
    }

    @Test fun `read permission failure is recoverable`() = runTest(main.dispatcher) {
        val vm = LogsViewModel(FakeRepository().apply { readFailure = SecurityException() })
        vm.open()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.failed)
    }

    @Test fun `empty logs do not start export`() = runTest(main.dispatcher) {
        val vm = LogsViewModel(FakeRepository().apply { snapshot = LogSnapshot("") })
        vm.open()
        advanceUntilIdle()
        vm.requestExport()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.exporting)
    }

    @Test fun `chooses destination once and exports through repository`() = runTest(main.dispatcher) {
        val repository = FakeRepository()
        val vm = LogsViewModel(repository)
        vm.open()
        advanceUntilIdle()
        vm.effects.test {
            vm.requestExport()
            vm.requestExport()
            advanceUntilIdle()
            assertEquals(LogsEffect.ChooseDestination, awaitItem())
            expectNoEvents()
            vm.destinationSelected("content://logs/document")
            advanceUntilIdle()
            assertEquals(LogsEffect.ExportSucceeded, awaitItem())
            assertEquals(listOf("content://logs/document"), repository.destinations)
            assertFalse(vm.uiState.value.exporting)
        }
    }

    @Test fun `cancelled document chooser re-enables export`() = runTest(main.dispatcher) {
        val vm = LogsViewModel(FakeRepository())
        vm.open()
        advanceUntilIdle()
        vm.requestExport()
        vm.destinationSelected(null)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.exporting)
    }

    @Test fun `unavailable document chooser reports failure and clears busy state`() = runTest(main.dispatcher) {
        val vm = LogsViewModel(FakeRepository())
        vm.open()
        advanceUntilIdle()
        vm.effects.test {
            vm.requestExport()
            advanceUntilIdle()
            assertEquals(LogsEffect.ChooseDestination, awaitItem())
            vm.destinationUnavailable()
            advanceUntilIdle()
            assertEquals(LogsEffect.ExportFailed, awaitItem())
            assertFalse(vm.uiState.value.exporting)
        }
    }

    @Test fun `export permission failure leaves snapshot available`() = runTest(main.dispatcher) {
        val vm = LogsViewModel(FakeRepository().apply { exportFailure = SecurityException() })
        vm.open()
        advanceUntilIdle()
        vm.effects.test {
            vm.requestExport()
            advanceUntilIdle()
            assertEquals(LogsEffect.ChooseDestination, awaitItem())
            vm.destinationSelected("content://logs/document")
            advanceUntilIdle()
            assertEquals(LogsEffect.ExportFailed, awaitItem())
            assertEquals("recent", vm.uiState.value.content)
            assertFalse(vm.uiState.value.exporting)
        }
    }

    @Test fun `export failure emits notice and allows retry`() = runTest(main.dispatcher) {
        val repository = FakeRepository().apply { exportFailure = IOException() }
        val vm = LogsViewModel(repository)
        vm.open()
        advanceUntilIdle()
        vm.effects.test {
            vm.requestExport()
            advanceUntilIdle()
            assertEquals(LogsEffect.ChooseDestination, awaitItem())
            vm.destinationSelected("content://logs/document")
            advanceUntilIdle()
            assertEquals(LogsEffect.ExportFailed, awaitItem())
            assertFalse(vm.uiState.value.exporting)
            repository.exportFailure = null
            vm.requestExport()
            advanceUntilIdle()
            assertEquals(LogsEffect.ChooseDestination, awaitItem())
            vm.destinationSelected("content://logs/document")
            advanceUntilIdle()
            assertEquals(LogsEffect.ExportSucceeded, awaitItem())
        }
    }
}
