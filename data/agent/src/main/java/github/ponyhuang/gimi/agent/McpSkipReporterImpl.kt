package github.ponyhuang.gimi.agent

import github.ponyhuang.gimi.domain.mcp.model.McpSkippedServer
import github.ponyhuang.gimi.domain.mcp.repository.McpSkipReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AdkMcpSkipReporter @Inject constructor() : McpSkipReporter {

    private val _skipped = MutableStateFlow<List<McpSkippedServer>>(emptyList())
    override val skipped: StateFlow<List<McpSkippedServer>> = _skipped.asStateFlow()

    override fun publish(skipped: List<McpSkippedServer>) {
        _skipped.value = skipped
    }
}
