package github.ponyhuang.gimi.feature.settings

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun sandboxNavigationUsesItsOwnDestinationEffect() = runTest {
        val viewModel = SettingsViewModel()
        viewModel.effects.test {
            viewModel.onAction(SettingsAction.OpenSandbox)
            assertEquals(SettingsEffect.NavigateToSandbox, awaitItem())
        }
    }

    @Test
    fun shizukuPageOpensWithoutAnyHomeSwitchState() = runTest {
        val viewModel = SettingsViewModel()
        viewModel.effects.test {
            viewModel.onAction(SettingsAction.OpenMobileUse)
            assertEquals(SettingsEffect.NavigateToMobileUse, awaitItem())
        }
    }
}
