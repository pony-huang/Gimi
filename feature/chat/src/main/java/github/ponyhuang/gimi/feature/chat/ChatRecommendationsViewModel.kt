package github.ponyhuang.gimi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 向聊天空状态投影全局推荐，不把推荐复制进会话状态。 */
@HiltViewModel
class ChatRecommendationsViewModel @Inject constructor(
    repository: RecommendationRepository,
) : ViewModel() {
    val recommendations: StateFlow<List<AgentRecommendation>> = repository.state
        .map { state ->
            if (state.settings.enabled) state.snapshot?.items.orEmpty() else emptyList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = repository.state.value.let { state ->
                if (state.settings.enabled) state.snapshot?.items.orEmpty() else emptyList()
            },
        )
}
