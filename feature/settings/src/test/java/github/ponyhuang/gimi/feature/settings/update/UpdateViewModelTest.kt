package github.ponyhuang.gimi.feature.settings.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.appupdate.model.AppUpdateInfo
import github.ponyhuang.gimi.domain.appupdate.model.AppVersion
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateCheckResult
import github.ponyhuang.gimi.feature.settings.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val updateInfo = AppUpdateInfo(
        version = AppVersion(0, 2, 0, null),
        tagName = "v0.2.0",
        title = "v0.2.0",
        changelog = "bug fixes",
        assets = emptyList(),
        publishedAt = null,
    )

    private fun fakeContext(canInstall: Boolean = false): Context {
        val packageInfo = PackageInfo().apply { versionName = "0.1.1-alpha" }
        val packageManager = mockk<PackageManager> {
            every { getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
            every { canRequestPackageInstalls() } returns canInstall
        }
        return mockk {
            every { this@mockk.packageManager } returns packageManager
            every { packageName } returns "github.ponyhuang.gimi"
        }
    }

    @Test
    fun `checkNow shows dialog when update available`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAppUpdateRepository(
                checkResult = UpdateCheckResult.UpdateAvailable(updateInfo),
                resultState = AppUpdateState.Available(updateInfo, "0.1.1-alpha"),
            )
            val viewModel = UpdateViewModel(fakeContext(), repository)
            backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.onAction(UpdateAction.CheckNow)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.dialogVisible)
            assertEquals(1, repository.manualChecks)
        }

    @Test
    fun `checkNow toasts when up to date`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAppUpdateRepository(
            checkResult = UpdateCheckResult.UpToDate,
            resultState = AppUpdateState.Idle,
        )
        val viewModel = UpdateViewModel(fakeContext(), repository)

        viewModel.effects.test {
            viewModel.onAction(UpdateAction.CheckNow)
            advanceUntilIdle()
            assertEquals(
                UpdateEffect.ShowToast(R.string.update_up_to_date),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(viewModel.uiState.value.dialogVisible)
    }

    @Test
    fun `checkNow reopens dialog without new request when already available`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAppUpdateRepository(
                checkResult = UpdateCheckResult.UpdateAvailable(updateInfo),
                resultState = AppUpdateState.Available(updateInfo, "0.1.1-alpha"),
            )
            repository.mutableState.value = AppUpdateState.Available(updateInfo, "0.1.1-alpha")
            val viewModel = UpdateViewModel(fakeContext(), repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(UpdateAction.CheckNow)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.dialogVisible)
            assertEquals(0, repository.manualChecks)
        }

    @Test
    fun `checkNow reopens current progress without new request while downloading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAppUpdateRepository(
                checkResult = UpdateCheckResult.UpdateAvailable(updateInfo),
                resultState = AppUpdateState.Available(updateInfo, "0.1.1-alpha"),
            )
            repository.mutableState.value = AppUpdateState.Downloading(updateInfo, 0.37f)
            val viewModel = UpdateViewModel(fakeContext(), repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(UpdateAction.CheckNow)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.dialogVisible)
            assertEquals(AppUpdateState.Downloading(updateInfo, 0.37f), viewModel.uiState.value.status)
            assertEquals(0, repository.manualChecks)
        }

    @Test
    fun `confirmInstall launches unknown sources settings when permission missing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAppUpdateRepository(
                checkResult = UpdateCheckResult.UpToDate,
                resultState = AppUpdateState.Idle,
            )
            repository.mutableState.value = AppUpdateState.Downloaded(
                updateInfo,
                apkPath = "/cache/updates/Gimi-v0.2.0-arm64-v8a.apk",
                signatureMismatch = false,
            )
            val viewModel = UpdateViewModel(fakeContext(canInstall = false), repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // isReturnDefaultValues 下 Uri.parse 返回 null 会让 toUri() 抛 NPE，
            // 这里用 mockk 静态拦截。
            mockkStatic(Uri::class)
            every { Uri.parse(any()) } returns mockk()
            try {
                viewModel.effects.test {
                    viewModel.onAction(UpdateAction.ConfirmInstall)
                    advanceUntilIdle()
                    // 本地单测的 android.content.Intent 是空壳（isReturnDefaultValues），
                    // 只能断言发出了启动意图，无法校验 action。
                    assertTrue(awaitItem() is UpdateEffect.LaunchIntent)
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                unmockkStatic(Uri::class)
            }
        }

    @Test
    fun `dismissDialog resets repository state`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAppUpdateRepository(
            checkResult = UpdateCheckResult.UpToDate,
            resultState = AppUpdateState.Idle,
        )
        repository.mutableState.value = AppUpdateState.Available(updateInfo, "0.1.1-alpha")
        val viewModel = UpdateViewModel(fakeContext(), repository)

        viewModel.onAction(UpdateAction.DismissDialog)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.dialogVisible)
        assertEquals(AppUpdateState.Idle, repository.state.value)
    }

    private class FakeAppUpdateRepository(
        private val checkResult: UpdateCheckResult,
        private val resultState: AppUpdateState,
    ) : AppUpdateRepository {
        val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
        override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()
        var manualChecks = 0
            private set

        override suspend fun checkForUpdate(manual: Boolean): UpdateCheckResult {
            if (manual) manualChecks++
            mutableState.value = resultState
            return checkResult
        }

        override fun startDownload(info: AppUpdateInfo) = Unit

        override fun cancelDownload() = Unit

        override fun reset() {
            mutableState.value = AppUpdateState.Idle
        }
    }
}
