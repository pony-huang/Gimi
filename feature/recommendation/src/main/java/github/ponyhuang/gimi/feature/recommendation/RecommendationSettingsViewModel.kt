package github.ponyhuang.gimi.feature.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSettings
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RecommendationSettingsViewModel @Inject constructor(
    private val repository: RecommendationRepository,
) : ViewModel() {
    val uiState: StateFlow<RecommendationSettingsUiState> = repository.state
        .map(RecommendationState::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.state.value.toUiState(),
        )

    fun onAction(action: RecommendationSettingsAction) {
        when (action) {
            is RecommendationSettingsAction.SetEnabled -> repository.setEnabled(action.enabled)
            is RecommendationSettingsAction.SetIntervalHours ->
                repository.setIntervalHours(action.intervalHours)
            RecommendationSettingsAction.RefreshNow -> repository.requestRefresh()
        }
    }
}

private fun RecommendationState.toUiState() = RecommendationSettingsUiState(
    enabled = settings.enabled,
    intervalHours = settings.intervalHours,
    intervals = RecommendationSettings.SUPPORTED_INTERVAL_HOURS,
    generatedAtEpochMillis = snapshot?.generatedAtEpochMillis,
    refreshStatus = refreshStatus,
    lastError = lastError,
    retryDelaySeconds = retryDelaySeconds,
)

