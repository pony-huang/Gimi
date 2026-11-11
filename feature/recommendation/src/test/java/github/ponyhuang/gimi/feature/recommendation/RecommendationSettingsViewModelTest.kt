package github.ponyhuang.gimi.feature.recommendation

import github.ponyhuang.gimi.core.testing.MainDispatcherRule
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveFastModelAvailabilityUseCase
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appearance = FakeAppearanceRepository()
    private val repository = mockk<RecommendationRepository> {
        every { state } returns MutableStateFlow(RecommendationState())
    }
    private val toolAccess = mockk<ToolAccessRepository> {
        every { defaultToolAccessMode } returns MutableStateFlow(ToolAccessMode.ON_DEMAND)
    }
    private val observeFastModelAvailability = mockk<ObserveFastModelAvailabilityUseCase> {
        every { this@mockk.invoke() } returns flowOf(true)
    }

    @Test
    fun initialStateRestoresSavedTheme() {
        appearance.themeMode.value = ThemeMode.DARK

        assertEquals(ThemeMode.DARK, createViewModel().uiState.value.themeMode)
    }

    @Test
    fun changingThemePersistsSelectionAndUpdatesUiState() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM).forEach { mode ->
            viewModel.onAction(RecommendationSettingsAction.SetThemeMode(mode))
            runCurrent()

            assertEquals(mode, appearance.themeMode.value)
            assertEquals(mode, viewModel.uiState.value.themeMode)
            assertEquals(ToolAccessMode.ON_DEMAND, viewModel.uiState.value.toolAccessMode)
        }
    }

    private fun createViewModel() = RecommendationSettingsViewModel(
        repository = repository,
        toolAccessRepository = toolAccess,
        appearanceRepository = appearance,
        observeFastModelAvailability = observeFastModelAvailability,
    )

    private class FakeAppearanceRepository : AppearanceRepository {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

        override fun setThemeMode(mode: ThemeMode) {
            themeMode.value = mode
        }
    }
}
