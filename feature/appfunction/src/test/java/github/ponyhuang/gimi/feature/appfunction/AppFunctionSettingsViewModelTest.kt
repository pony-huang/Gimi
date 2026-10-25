package github.ponyhuang.gimi.feature.appfunction

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.appfunction.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 保存失败不能把应用呈现为已开启，刷新需可重试并防止重复查询。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppFunctionSettingsViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    private class Repository : AppFunctionRepository {
        override val catalog = MutableStateFlow(AppFunctionCatalog())
        override val enabledPackages = MutableStateFlow<Set<String>>(emptySet())
        override val revision = MutableStateFlow(0L)
        var refreshCount = 0
        var gate: CompletableDeferred<Unit>? = null
        var failSave = false
        override suspend fun refresh() { refreshCount++; gate?.await() }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            if (failSave) error("save failed")
            enabledPackages.value = if (enabled) enabledPackages.value + packageName else enabledPackages.value - packageName
        }
    }

    @Test fun duplicateRefreshIsCoalescedAndCanRunAgainAfterCompletion() = runTest {
        val repository = Repository().apply { gate = CompletableDeferred() }
        val vm = AppFunctionSettingsViewModel(repository)
        vm.onAction(AppFunctionAction.Refresh)
        vm.onAction(AppFunctionAction.Refresh)
        runCurrent()
        assertEquals(1, repository.refreshCount)
        assertTrue(vm.uiState.value.isRefreshing)
        repository.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        vm.onAction(AppFunctionAction.Refresh)
        advanceUntilIdle()
        assertEquals(2, repository.refreshCount)
    }

    @Test fun failedSaveKeepsSwitchOffAndSuccessfulRetryUpdatesIt() = runTest {
        val repository = Repository().apply { failSave = true }
        val vm = AppFunctionSettingsViewModel(repository)
        vm.onAction(AppFunctionAction.SetEnabled("notes", true))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saveFailed)
        assertTrue(vm.uiState.value.enabledPackages.isEmpty())
        repository.failSave = false
        vm.onAction(AppFunctionAction.SetEnabled("notes", true))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.saveFailed)
        assertEquals(setOf("notes"), vm.uiState.value.enabledPackages)
    }
}
