package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.data.agent.a2a.REMOTE_AGENT_CARD_TIMEOUT_MS
import github.ponyhuang.gimi.data.agent.a2a.failureSummary
import github.ponyhuang.gimi.data.agent.a2a.fetchRemoteAgentCard
import github.ponyhuang.gimi.data.agent.a2a.httpClientFor
import github.ponyhuang.gimi.data.agent.a2a.toSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentProbeResult
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentConnectionTester
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull
import org.a2aproject.sdk.client.http.A2AHttpClient

/** 基于真实 A2A 客户端栈的连通性探测：成功即代表卡片可拉取、可解析、可建立客户端。 */
@Singleton
class AdkRemoteAgentConnectionTester @Inject constructor(
    private val baseHttpClient: A2AHttpClient,
) : RemoteAgentConnectionTester {

    override suspend fun test(config: RemoteAgentConfig): RemoteAgentProbeResult =
        withTimeoutOrNull(REMOTE_AGENT_CARD_TIMEOUT_MS) {
            // 直接拉卡片并裁剪快照：与 buildRemoteA2aAgent 同一套解析路径，但把卡片内容带回给调用方。
            runCatching { fetchRemoteAgentCard(config, baseHttpClient).toSnapshot() }.fold(
                onSuccess = { RemoteAgentProbeResult(reachable = true, card = it) },
                onFailure = { failure ->
                    RemoteAgentProbeResult(reachable = false, errorMessage = failureSummary(failure))
                },
            )
        } ?: RemoteAgentProbeResult(reachable = false)
}
