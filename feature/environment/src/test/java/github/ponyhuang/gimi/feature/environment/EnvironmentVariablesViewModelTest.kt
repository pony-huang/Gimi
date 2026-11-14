package github.ponyhuang.gimi.feature.environment

import androidx.lifecycle.viewModelScope
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.environment.*
import github.ponyhuang.gimi.domain.sandbox.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 变量草稿编辑与保存反馈；失败必须在保存栏可见，而不是只在页面底部出现泛化错误。 */
@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentVariablesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val status = EnvironmentStatus(available = true, installed = true, enabled = true,
        runtimeVersion = RuntimeVersion(66, "1.0.0", 1), bundledRuntimeVersion = RuntimeVersion(66, "1.0.0", 1))
    private lateinit var local: FakeEnvironmentRepository
    private lateinit var viewModel: EnvironmentViewModel
    private lateinit var sandbox: SandboxSettingsRepository

    /** 记录保存的变量与注入的保存失败；其余执行类接口在本测试中不使用。 */
    private class FakeEnvironmentRepository(initial: EnvironmentStatus) : EnvironmentRepository {
        var current = initial
        var stored: Map<String, String>? = null
        var saveError: Exception? = null
        override suspend fun status(): EnvironmentStatus = current
        override suspend fun companionApk() = "runtime.apk"
        override suspend fun install() = Unit
        override suspend fun setEnabled(enabled: Boolean) { current = current.copy(enabled = enabled) }
        override suspend fun variables(): Map<String, String> = stored ?: mapOf("EXISTING" to "1")
        override suspend fun saveVariables(values: Map<String, String>) {
            saveError?.let { throw it }
            stored = values
        }
        override suspend fun listDirectory(path: String) = emptyList<EnvironmentDirectoryEntry>()
        override suspend fun execute(session: String, command: String, timeoutMillis: Long): EnvironmentCommandResult = error("Unused")
        override suspend fun readFile(session: String, path: String): ByteArray = error("Unused")
        override suspend fun writeFile(session: String, path: String, content: ByteArray) = Unit
        override suspend fun startJob(session: String, command: String, keepAlive: Boolean): String = error("Unused")
        override suspend fun queryJob(session: String, id: String): EnvironmentJobStatus = error("Unused")
        override suspend fun stopJob(session: String, id: String) = Unit
    }

    @Before fun setUp() {
        local = FakeEnvironmentRepository(status)
        sandbox = object : SandboxSettingsRepository {
            override val configuration = MutableStateFlow(SandboxConfiguration())
            override val revision = MutableStateFlow(0L)
            override suspend fun setEnabled(enabled: Boolean) = Unit
            override suspend fun saveConnection(connection: SandboxConnection) = Unit
        }
        viewModel = EnvironmentViewModel(local, sandbox)
    }
    @After fun tearDown() { viewModel.viewModelScope.cancel() }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { viewModel.viewModelScope.cancel() }
    }

    private suspend fun TestScope.loadAddEdit(name: String, value: String) {
        runCurrent()
        viewModel.loadVariables(); runCurrent()
        viewModel.addVariable()
        viewModel.editVariable(viewModel.uiState.value.variables.last().id, name, value)
    }

    @Test fun savingEditedDraftsPersistsEntriesAndClearsDirty() = runViewModelTest {
        loadAddEdit("NEW_VAR", "2"); runCurrent()
        viewModel.saveVariables(); runCurrent()
        assertEquals(mapOf("EXISTING" to "1", "NEW_VAR" to "2"), local.stored)
        assertTrue(viewModel.uiState.value.saved)
        assertFalse(viewModel.uiState.value.variablesDirty)
        assertNull(viewModel.uiState.value.saveError)
        // 保存成功后进入页面的重载守卫放行，但草稿不被无草稿状态误判为空。
        assertTrue(viewModel.uiState.value.variablesLoaded)
    }
    @Test fun invalidNameFailsSaveWithValidationNoticeAndKeepsDrafts() = runViewModelTest {
        loadAddEdit("1bad-name", "2"); runCurrent()
        viewModel.saveVariables(); runCurrent()
        assertEquals(EnvironmentSaveError.Validation, viewModel.uiState.value.saveError)
        assertTrue(viewModel.uiState.value.variablesDirty)
        assertNull(local.stored)
        assertFalse(viewModel.uiState.value.saved)
    }
    @Test fun duplicateNamesFailValidationWithoutCallingRuntime() = runViewModelTest {
        runCurrent()
        viewModel.loadVariables(); runCurrent()
        viewModel.addVariable()
        viewModel.editVariable(viewModel.uiState.value.variables.first().id, "SAME", "a")
        viewModel.editVariable(viewModel.uiState.value.variables.last().id, "SAME", "b")
        viewModel.saveVariables(); runCurrent()
        assertEquals(EnvironmentSaveError.Validation, viewModel.uiState.value.saveError)
        assertNull(local.stored)
    }
    @Test fun runtimeFailureFailsSaveWithConnectionNoticeWithoutPageError() = runViewModelTest {
        loadAddEdit("NEW_VAR", "2"); runCurrent()
        local.saveError = IOException("Runtime update required")
        viewModel.saveVariables(); runCurrent()
        assertEquals(EnvironmentSaveError.Connection, viewModel.uiState.value.saveError)
        assertTrue(viewModel.uiState.value.variablesDirty)
        // 保存失败不进入页面底部泛化错误态，反馈集中在保存栏。
        assertFalse(viewModel.uiState.value.error)
        assertNull(local.stored)
    }
    @Test fun editingAfterFailureClearsSaveNotice() = runViewModelTest {
        loadAddEdit("1bad", "2"); runCurrent()
        viewModel.saveVariables(); runCurrent()
        assertEquals(EnvironmentSaveError.Validation, viewModel.uiState.value.saveError)
        val id = viewModel.uiState.value.variables.last().id
        viewModel.editVariable(id, "GOOD_NAME", "2"); runCurrent()
        assertNull(viewModel.uiState.value.saveError)
        assertTrue(viewModel.uiState.value.variablesDirty)
    }
    @Test fun reloadAfterDeleteAllKeepsDeletedDraftsMarkedLoaded() = runViewModelTest {
        runCurrent()
        viewModel.loadVariables(); runCurrent()
        viewModel.removeVariable(viewModel.uiState.value.variables.first().id); runCurrent()
        assertTrue(viewModel.uiState.value.variables.isEmpty())
        assertTrue(viewModel.uiState.value.variablesDirty)
        // 重入变量页时以 variablesLoaded 为准，空列表不再触发重载回滚删除。
        assertTrue(viewModel.uiState.value.variablesLoaded)
    }
}
