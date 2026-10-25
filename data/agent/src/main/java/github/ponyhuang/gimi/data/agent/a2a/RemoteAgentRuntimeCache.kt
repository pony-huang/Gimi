package github.ponyhuang.gimi.data.agent.a2a

import com.google.adk.kt.a2a.agent.BaseRemoteA2AAgent
import com.google.adk.kt.agents.BaseAgent
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.SkippedRemoteAgent
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentSkipReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.a2aproject.sdk.client.http.A2AHttpClient
import org.a2aproject.sdk.spec.AgentCard

/**
 * 远程 A2A agent 的构建期运行时缓存：把启用的 [RemoteAgentConfig] 装配为
 * [BaseRemoteA2AAgent]（构建期拉取 agent card 并建立 JSON-RPC 客户端），失败降级为跳过。
 *
 * 缓存的是**卡片**（可复用的网络产物），而 agent 实例每次装配新建——BaseAgent 挂树时
 * 写入 parentAgent，同一实例二次挂载会抛 "already has a parent"，因此跨树复用实例
 * 是错误设计。配置身份（id + 名称 + 地址 + 凭据）变化即卡片缓存整体失效；同一配置
 * 在进程生命周期内复用已解析卡片，远端卡片变更需配置改动或重启进程后生效（v1 取舍）。
 * 卡片拉取失败的配置不做负缓存，每次 Agent 重建都会重试，超时上限
 * [REMOTE_AGENT_CARD_TIMEOUT_MS]；与其他贡献方 revision 变化触发的重建由此处的
 * 缓存吸收，不会重复发起网络请求。
 */
@Singleton
class RemoteAgentRuntimeCache @Inject constructor(
    private val repository: RemoteAgentRepository,
    private val baseHttpClient: A2AHttpClient,
    private val skipReporter: RemoteAgentSkipReporter,
) {
    private data class CachedCard(
        val config: RemoteAgentConfig,
        val card: AgentCard,
    )

    /** 单个配置的一次卡片解析结果：[card] 为 null 时 [failureReason] 描述原因（超时为 null）。 */
    private data class CardOutcome(
        val card: AgentCard?,
        val failureReason: String?,
    )

    private val mutex = Mutex()
    private val cardCache = mutableMapOf<String, CachedCard>()
    private var cachedSignature: List<List<String>> = emptyList()

    /** 返回当前应挂到 agent 树上的远程 sub-agent；跳过结果整体发布到 [skipReporter]。 */
    suspend fun currentSubAgents(spec: AgentBuildSpec): List<BaseAgent> = mutex.withLock {
        val enabled = repository.currentAgents().filter { it.isEnabled }
        val signature = enabled.map { listOf(it.id, it.name, it.endpointUrl, it.bearerToken) }
        if (signature != cachedSignature) {
            cardCache.clear()
            cachedSignature = signature
        }

        val skipped = mutableListOf<SkippedRemoteAgent>()
        val seenNames = mutableSetOf<String>()
        val agents = enabled.mapNotNull { config ->
            val displayName = config.agentTreeName()
            // agent 名是 transfer_to_agent 的委派目标，必须树内唯一；重名配置后来者跳过。
            if (!seenNames.add(displayName)) {
                skipped += SkippedRemoteAgent(config.id, displayName, REASON_DUPLICATE_NAME)
                return@mapNotNull null
            }
            val outcome = resolveCard(config)
            if (outcome.card == null) {
                skipped += SkippedRemoteAgent(
                    remoteAgentId = config.id,
                    displayName = displayName,
                    reason = outcome.failureReason ?: REASON_UNAVAILABLE,
                )
                return@mapNotNull null
            }
            // 每次装配新建实例：同一卡片挂到多棵树/多次重建都安全。
            newRemoteA2aAgent(config, outcome.card, baseHttpClient)
        }
        skipReporter.publish(skipped)
        agents
    }

    /** 命中缓存或拉取 agent card；网络异常归因到摘要，超时整体返回 null 原因。 */
    private suspend fun resolveCard(config: RemoteAgentConfig): CardOutcome {
        cardCache[config.id]?.let { return CardOutcome(it.card, null) }
        val outcome = withTimeoutOrNull(REMOTE_AGENT_CARD_TIMEOUT_MS) {
            runCatching { fetchRemoteAgentCard(config, baseHttpClient) }.fold(
                onSuccess = { card -> CardOutcome(card, null) },
                onFailure = { failure -> CardOutcome(null, failureSummary(failure)) },
            )
        } ?: CardOutcome(null, null)
        outcome.card?.let { card -> cardCache[config.id] = CachedCard(config, card) }
        return outcome
    }

    private companion object {
        const val REASON_DUPLICATE_NAME: String = "duplicate agent name"
        const val REASON_UNAVAILABLE: String = "agent card unavailable"
    }
}
