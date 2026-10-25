package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.domain.a2a.model.SkippedRemoteAgent
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentSkipReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AdkRemoteAgentSkipReporter @Inject constructor() : RemoteAgentSkipReporter {

    private val _skipped = MutableStateFlow<List<SkippedRemoteAgent>>(emptyList())
    override val skipped: StateFlow<List<SkippedRemoteAgent>> = _skipped.asStateFlow()

    override fun publish(skipped: List<SkippedRemoteAgent>) {
        _skipped.value = skipped
    }
}
