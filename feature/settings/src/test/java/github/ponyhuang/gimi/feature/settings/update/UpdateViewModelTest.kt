package github.ponyhuang.gimi.feature.settings.update

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.appupdate.model.AppUpdateInfo
import github.ponyhuang.gimi.domain.appupdate.model.AppVersion
import github.ponyhuang.gimi.domain.appupdate.repository.AppInstallEnvironment
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.domain.appupdate.repository.UpdateCheckResult
import github.ponyhuang.gimi.feature.settings.R
import io.mockk.every
import io.mockk.mockk
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

    private fun fakeEnvironment(
        canInstall: Boolean = false,
        versionName: String? = "0.1.1-alpha",
        apkContentUri: String? = "content://github.ponyhuang.gimi.fileprovider/updates/apk.apk",
    ): AppInstallEnvironment = mockk {
        every { canRequestPackageInstalls() } returns canInstall
        every { currentVersionName() } returns versionName
        every { apkContentUri(any()) } returns apkContentUri
    }

    @Test
    fun `checkNow shows dialog when update available`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeAppUpdateRepository(
                checkResult = UpdateCheckResult.UpdateAvailable(updateInfo),
                resultState = AppUpdateState.Available(updateInfo, "0.1.1-alpha"),
            )
            val viewModel = UpdateViewModel(repository, fakeEnvironment())
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
        val viewModel = UpdateViewModel(repository, fakeEnvironment())

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
            val viewModel = UpdateViewModel(repository, fakeEnvironment())
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
            val viewModel = UpdateViewModel(repository, fakeEnvironment())
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
            val viewModel = UpdateViewModel(repository, fakeEnvironment(canInstall = false))
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onAction(UpdateAction.ConfirmInstall)
                advanceUntilIdle()
                assertEquals(UpdateEffect.OpenUnknownSourceSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `confirmInstall emits apk content uri when permission granted`() =
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
            val viewModel = UpdateViewModel(repository, fakeEnvironment(canInstall = true))
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onAction(UpdateAction.ConfirmInstall)
                advanceUntilIdle()
                assertEquals(
                    UpdateEffect.InstallApk(
                        "content://github.ponyhuang.gimi.fileprovider/updates/apk.apk",
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissDialog resets repository state`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeAppUpdateRepository(
            checkResult = UpdateCheckResult.UpToDate,
            resultState = AppUpdateState.Idle,
        )
        repository.mutableState.value = AppUpdateState.Available(updateInfo, "0.1.1-alpha")
        val viewModel = UpdateViewModel(repository, fakeEnvironment())

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
