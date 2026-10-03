package github.ponyhuang.gimi.feature.settings

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val enabled = MutableStateFlow(true)
    private val repository = mockk<MobileUseRepository> {
        every { enabled } returns this@SettingsViewModelTest.enabled
        coEvery { setEnabled(any()) } answers { this@SettingsViewModelTest.enabled.value = firstArg() }
    }

    @Test
    fun localModelEntryNavigatesToLocalCatalog() = runTest {
        val viewModel = SettingsViewModel(repository)
        viewModel.effects.test {
            viewModel.onAction(SettingsAction.OpenLocalModels)
            assertEquals(SettingsEffect.NavigateToLocalModels, awaitItem())
        }
    }

    @Test
    fun mobileUseCanOnlyBeOpenedAfterEnablingAndPersistsToggle() = runTest {
        enabled.value = false
        val viewModel = SettingsViewModel(repository)
        assertFalse(viewModel.uiState.value.mobileUseEnabled)
        viewModel.effects.test {
            viewModel.onAction(SettingsAction.OpenMobileUse)
            runCurrent()
            expectNoEvents()
            viewModel.onAction(SettingsAction.SetMobileUseEnabled(true))
            runCurrent()
            assertTrue(viewModel.uiState.value.mobileUseEnabled)
            viewModel.onAction(SettingsAction.OpenMobileUse)
            assertEquals(SettingsEffect.NavigateToMobileUse, awaitItem())
            viewModel.onAction(SettingsAction.SetMobileUseEnabled(false))
            runCurrent()
            assertFalse(viewModel.uiState.value.mobileUseEnabled)
            viewModel.onAction(SettingsAction.OpenMobileUse)
            runCurrent()
            expectNoEvents()
        }
        coVerify(exactly = 1) { repository.setEnabled(true) }
        coVerify(exactly = 1) { repository.setEnabled(false) }
    }

    @Test
    fun navigationAndRepeatedChangesWaitForShutdownToFinish() = runTest {
        val shutdown = CompletableDeferred<Unit>()
        coEvery { repository.setEnabled(false) } coAnswers {
            shutdown.await()
            enabled.value = false
        }
        val viewModel = SettingsViewModel(repository)
        viewModel.effects.test {
            viewModel.onAction(SettingsAction.SetMobileUseEnabled(false))
            runCurrent()
            assertTrue(viewModel.uiState.value.mobileUseUpdating)
            viewModel.onAction(SettingsAction.OpenMobileUse)
            viewModel.onAction(SettingsAction.SetMobileUseEnabled(true))
            runCurrent()
            expectNoEvents()
            coVerify(exactly = 0) { repository.setEnabled(true) }
            shutdown.complete(Unit)
            runCurrent()
            assertFalse(viewModel.uiState.value.mobileUseEnabled)
            assertFalse(viewModel.uiState.value.mobileUseUpdating)
        }
    }

}
