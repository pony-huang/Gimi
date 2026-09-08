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
    fun navigationActionsEmitMatchingEffects() = runTest {
        val viewModel = SettingsViewModel()

        viewModel.effects.test {
            viewModel.onAction(SettingsAction.OpenModelService)
            assertEquals(SettingsEffect.NavigateToModelService, awaitItem())
            viewModel.onAction(SettingsAction.OpenDefaultModels)
            assertEquals(SettingsEffect.NavigateToDefaultModels, awaitItem())
            viewModel.onAction(SettingsAction.OpenVoiceWake)
            assertEquals(SettingsEffect.NavigateToVoiceWake, awaitItem())
            viewModel.onAction(SettingsAction.OpenMcpServers)
            assertEquals(SettingsEffect.NavigateToMcpServers, awaitItem())
            viewModel.onAction(SettingsAction.OpenSkills)
            assertEquals(SettingsEffect.NavigateToSkills, awaitItem())
            viewModel.onAction(SettingsAction.OpenWorkFiles)
            assertEquals(SettingsEffect.NavigateToWorkFiles, awaitItem())
            viewModel.onAction(SettingsAction.OpenPermissions)
            assertEquals(SettingsEffect.NavigateToPermissions, awaitItem())
            viewModel.onAction(SettingsAction.OpenRecommendations)
            assertEquals(SettingsEffect.NavigateToRecommendations, awaitItem())
            viewModel.onAction(SettingsAction.OpenMemory)
            assertEquals(SettingsEffect.NavigateToMemory, awaitItem())
            viewModel.onAction(SettingsAction.OpenToolAuthorization)
            assertEquals(SettingsEffect.NavigateToToolAuthorization, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
