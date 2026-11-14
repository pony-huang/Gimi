package github.ponyhuang.gimi.feature.environment

import github.ponyhuang.gimi.domain.environment.EnvironmentJobStatus
import androidx.lifecycle.viewModelScope
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.environment.*
import github.ponyhuang.gimi.domain.sandbox.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeUpdateViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val initial = EnvironmentStatus(available = true, installed = true, enabled = true,
        runtimeVersion = RuntimeVersion(65, "old", 1), bundledRuntimeVersion = RuntimeVersion(66, "new", 1))
    private val local = FakeEnvironmentRepository(initial)
    private lateinit var viewModel: EnvironmentViewModel
    private lateinit var sandbox: SandboxSettingsRepository

    /** 记录更新前的停止顺序与失败，其他环境操作在本测试中不使用。 */
    private class FakeEnvironmentRepository(var current: EnvironmentStatus) : EnvironmentRepository {
        var stops = 0
        var apks = 0
        var installs = 0
        var stopError: Exception? = null
        var statusError: IOException? = null
        override suspend fun status(): EnvironmentStatus { statusError?.let { throw it }; return current }
        override suspend fun companionApk(): String { apks++; return "runtime.apk" }
        override suspend fun setEnabled(enabled: Boolean) {
            stopError?.let { throw it }
            stops++
            current = current.copy(enabled = enabled, installing = false)
        }
        override suspend fun install() { installs++ }
        override suspend fun variables() = emptyMap<String, String>()
        override suspend fun saveVariables(values: Map<String, String>) = Unit
        override suspend fun listDirectory(path: String) = emptyList<EnvironmentDirectoryEntry>()
        override suspend fun execute(session: String, command: String, timeoutMillis: Long): EnvironmentCommandResult = error("Unused")
        override suspend fun readFile(session: String, path: String): ByteArray = error("Unused")
        override suspend fun writeFile(session: String, path: String, content: ByteArray) = Unit
        override suspend fun startJob(session: String, command: String, keepAlive: Boolean): String = error("Unused")
        override suspend fun queryJob(session: String, id: String): EnvironmentJobStatus = error("Unused")
        override suspend fun stopJob(session: String, id: String) = Unit
    }

    @Before fun setUp() {
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

    @Test fun enabledRuntimeRequiresConfirmationAndCancelLeavesItRunning() = runViewModelTest {
        runCurrent()
        viewModel.requestRuntimeUpdate(); runCurrent()
        assertTrue(viewModel.uiState.value.confirmUpdate)
        viewModel.dismissRuntimeUpdate()
        assertFalse(viewModel.uiState.value.confirmUpdate)
        assertEquals(0, local.stops)
        assertEquals(0, local.apks)
    }
    @Test fun confirmingDeliversInstallerEvent() = runViewModelTest {
        runCurrent()
        viewModel.requestRuntimeUpdate(); runCurrent()
        var apk: String? = null
        backgroundScope.launch { apk = viewModel.packageInstalls.first() }
        viewModel.updateRuntime(); runCurrent()
        assertEquals("runtime.apk", apk)
        assertFalse(viewModel.uiState.value.confirmUpdate)
        assertEquals(1, local.stops)
        assertFalse(viewModel.uiState.value.local.enabled)
    }
    @Test fun stopFailureRetainsDialogAndReportsError() = runViewModelTest {
        runCurrent()
        viewModel.requestRuntimeUpdate(); runCurrent()
        local.stopError = IOException("stop failed")
        viewModel.updateRuntime(); runCurrent()
        assertTrue(viewModel.uiState.value.confirmUpdate)
        assertTrue(viewModel.uiState.value.error)
        assertEquals(0, local.apks)
    }
    @Test fun permissionFailureDoesNotLaunchInstaller() = runViewModelTest {
        runCurrent()
        viewModel.requestRuntimeUpdate(); runCurrent()
        local.stopError = SecurityException("permission denied")
        viewModel.updateRuntime(); runCurrent()
        assertTrue(viewModel.uiState.value.error)
        assertEquals(0, local.apks)
    }
    @Test fun idleRuntimeOpensInstallerDirectly() = runViewModelTest {
        local.current = initial.copy(enabled = false)
        runCurrent()
        var apk: String? = null
        backgroundScope.launch { apk = viewModel.packageInstalls.first() }
        viewModel.requestRuntimeUpdate(); runCurrent()
        assertEquals("runtime.apk", apk)
        assertEquals(0, local.stops)
    }
    @Test fun successfulVersionChangeClearsTransientProviderError() = runViewModelTest {
        runCurrent()
        local.statusError = IOException("provider restarting")
        advanceTimeBy(1500)
        runCurrent()
        assertTrue(viewModel.uiState.value.error)
        local.current = initial.copy(runtimeVersion = initial.bundledRuntimeVersion, enabled = false)
        local.statusError = null
        advanceTimeBy(1500)
        runCurrent()
        assertFalse(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.local.updateAvailable)
    }
    @Test fun connectionLossRetainsKnownInstallationAndBlocksReinstall() = runViewModelTest {
        runCurrent()
        local.current = initial.copy(installed = false, enabled = false, connectionAvailable = false)
        advanceTimeBy(1500)
        runCurrent()
        assertTrue(viewModel.uiState.value.local.installed)
        assertFalse(viewModel.uiState.value.local.connectionAvailable)
        assertTrue(viewModel.uiState.value.statusLoaded)
        viewModel.install()
        runCurrent()
        assertEquals(0, local.installs)
        assertEquals(0, local.apks)
        local.current = initial
        viewModel.refreshStatus()
        runCurrent()
        assertTrue(viewModel.uiState.value.local.connectionAvailable)
        assertFalse(viewModel.uiState.value.error)
    }
    @Test fun initialConnectionFailureKeepsInstallationUnconfirmed() = runViewModelTest {
        viewModel.viewModelScope.cancel()
        local.current = initial.copy(installed = false, enabled = false, connectionAvailable = false)
        viewModel = EnvironmentViewModel(local, sandbox)
        assertFalse(viewModel.uiState.value.statusLoaded)
        runCurrent()
        assertFalse(viewModel.uiState.value.local.connectionAvailable)
        assertFalse(viewModel.uiState.value.local.runtimeUsable)
        assertTrue(viewModel.uiState.value.statusLoaded)
        assertEquals(0, local.installs)
    }
    @Test fun pollingClearsUpdateNoticeAfterSuccessfulInstall() = runViewModelTest {
        runCurrent()
        assertTrue(viewModel.uiState.value.local.updateAvailable)
        local.current = initial.copy(runtimeVersion = initial.bundledRuntimeVersion, enabled = false)
        advanceTimeBy(1500)
        runCurrent()
        assertFalse(viewModel.uiState.value.local.updateAvailable)
    }
}
