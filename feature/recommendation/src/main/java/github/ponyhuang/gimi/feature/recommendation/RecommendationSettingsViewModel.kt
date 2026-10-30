package github.ponyhuang.gimi.feature.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveFastModelAvailabilityUseCase
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSettings
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RecommendationSettingsViewModel @Inject constructor(
    private val repository: RecommendationRepository,
    private val toolAccessRepository: ToolAccessRepository,
    observeFastModelAvailability: ObserveFastModelAvailabilityUseCase,
) : ViewModel() {
    val uiState: StateFlow<RecommendationSettingsUiState> = combine(
        repository.state,
        observeFastModelAvailability(),
        toolAccessRepository.defaultToolAccessMode,
    ) { state, fastModelReady, mode ->
        toUiState(state, fastModelReady).copy(toolAccessMode = mode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // stateIn 首帧兜底：combine 订阅后立即发出真实值，短暂窗口内沿用仓库当前状态。
        initialValue = toUiState(repository.state.value, fastModelReady = true).copy(
            toolAccessMode = toolAccessRepository.defaultToolAccessMode.value,
        ),
    )

    fun onAction(action: RecommendationSettingsAction) {
        when (action) {
            is RecommendationSettingsAction.SetToolAccessMode ->
                toolAccessRepository.setDefaultToolAccessMode(action.mode)
            is RecommendationSettingsAction.SetEnabled -> repository.setEnabled(action.enabled)
            is RecommendationSettingsAction.SetIntervalHours ->
                repository.setIntervalHours(action.intervalHours)
            RecommendationSettingsAction.RefreshNow -> repository.requestRefresh()
        }
    }
}

private fun toUiState(
    state: RecommendationState,
    fastModelReady: Boolean,
) = RecommendationSettingsUiState(
    enabled = state.settings.enabled,
    intervalHours = state.settings.intervalHours,
    intervals = RecommendationSettings.SUPPORTED_INTERVAL_HOURS,
    generatedAtEpochMillis = state.snapshot?.generatedAtEpochMillis,
    refreshStatus = state.refreshStatus,
    lastError = state.lastError,
    retryDelaySeconds = state.retryDelaySeconds,
    fastModelReady = fastModelReady,
)
