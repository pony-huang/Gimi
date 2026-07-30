package github.ponyhuang.gimi.feature.settings

import app.cash.turbine.test
import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository
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
        val viewModel = SettingsViewModel(repository, FakeAppFunctionRepository())

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
        val viewModel = SettingsViewModel(
            FakeChatDisplayRepository(),
            FakeAppFunctionRepository(),
        )

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
            viewModel.onAction(SettingsAction.OpenAppFunctions)
            assertEquals(SettingsEffect.NavigateToAppFunctions, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleActionDoesNotEmitEffect() = runTest {
        val viewModel = SettingsViewModel(
            FakeChatDisplayRepository(),
            FakeAppFunctionRepository(),
        )

        viewModel.effects.test {
            viewModel.onAction(SettingsAction.SetToolActivityVisible(false))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appFunctionsMenuRequiresRuntimeManagerSupport() =
        runTest(mainDispatcherRule.dispatcher) {
            val unsupported = SettingsViewModel(
                FakeChatDisplayRepository(),
                FakeAppFunctionRepository(AppFunctionsSupport.UNSUPPORTED_DEVICE),
            )
            val supported = SettingsViewModel(
                FakeChatDisplayRepository(),
                FakeAppFunctionRepository(AppFunctionsSupport.MISSING_SYSTEM_PERMISSION),
            )

            unsupported.uiState.test {
                assertFalse(awaitItem().showAppFunctions)
                cancelAndIgnoreRemainingEvents()
            }
            supported.uiState.test {
                assertTrue(awaitItem().showAppFunctions)
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

private class FakeAppFunctionRepository(
    support: AppFunctionsSupport = AppFunctionsSupport.AVAILABLE,
) : AppFunctionRepository {
    override val state = MutableStateFlow(AppFunctionCatalogState(support = support))
    override val revision = MutableStateFlow(0L)

    override suspend fun setFeatureEnabled(enabled: Boolean): Boolean = true

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean) = Unit

    override suspend fun setFunctionEnabled(key: AppFunctionKey, enabled: Boolean) = Unit

    override suspend fun execute(
        key: AppFunctionKey,
        arguments: Map<String, Any>,
    ): AppFunctionExecutionResult = AppFunctionExecutionResult.Success(Unit)
}
