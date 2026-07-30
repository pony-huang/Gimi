package github.ponyhuang.gimi.feature.settings

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    @Test
    fun toolActivitySwitchPassesThroughToRepository() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeChatDisplayRepository()
        val viewModel = SettingsViewModel(repository)

        viewModel.uiState.test {
            assertFalse(awaitItem().showToolActivity)

            viewModel.onAction(SettingsAction.SetToolActivityVisible(true))

            assertTrue(repository.showToolActivity.value)
            assertTrue(awaitItem().showToolActivity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun navigationActionsEmitMatchingEffects() = runTest {
        val viewModel = SettingsViewModel(FakeChatDisplayRepository())

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
            viewModel.onAction(SettingsAction.OpenToolAuthorization)
            assertEquals(SettingsEffect.NavigateToToolAuthorization, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleActionDoesNotEmitEffect() = runTest {
        val viewModel = SettingsViewModel(FakeChatDisplayRepository())

        viewModel.effects.test {
            viewModel.onAction(SettingsAction.SetToolActivityVisible(false))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeChatDisplayRepository : ChatDisplayRepository {
    private val mutableShowToolActivity = MutableStateFlow(false)
    override val showToolActivity: StateFlow<Boolean> = mutableShowToolActivity

    private val mutableDarkThemeOverride = MutableStateFlow<Boolean?>(null)
    override val darkThemeOverride: StateFlow<Boolean?> = mutableDarkThemeOverride

    override fun setShowToolActivity(show: Boolean) {
        mutableShowToolActivity.value = show
    }

    override fun setDarkThemeOverride(enabled: Boolean) {
        mutableDarkThemeOverride.value = enabled
    }
}
