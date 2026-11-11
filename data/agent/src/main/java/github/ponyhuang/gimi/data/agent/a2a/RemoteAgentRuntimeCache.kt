package github.ponyhuang.gimi.data.agent.a2a

import com.google.adk.kt.a2a.agent.BaseRemoteA2AAgent
import com.google.adk.kt.agents.BaseAgent
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.SkippedRemoteAgent
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentSkipReporter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * 拉取与装配失败使用一分钟冷却窗口；到期后的下一次执行触发重建重试。
 * 单次拉取的超时上限为 [REMOTE_AGENT_CARD_TIMEOUT_MS]。
 */
@Singleton
class RemoteAgentRuntimeCache @Inject constructor(
    private val repository: RemoteAgentRepository,
    private val baseHttpClient: A2AHttpClient,
    private val skipReporter: RemoteAgentSkipReporter,
    private val cardFetcher: RemoteAgentCardFetcher,
) {
    /** 同一配置的已解析卡片；实例须按树重新创建。 */
    private data class CachedCard(
        val config: RemoteAgentConfig,
        val card: AgentCard,
    )

    /** 单个配置的一次卡片解析结果：[card] 为 null 时 [failureReason] 描述原因（超时为 null）。 */
    private data class CardOutcome(
        val card: AgentCard?,
        val failureReason: String?,
    )

    private val lock = Any()
    internal var nanoTime: () -> Long = System::nanoTime
    private var nextRefreshAt: Long? = null
    private var refreshRevision = 0L

    /**
     * 失败卡片的冷却结果，防止其他贡献方重建时重复请求。
     * @property outcome 最近一次拉取或装配的失败摘要。
     * @property retryAt 允许重试的单调时钟纳秒值，与 [nanoTime] 使用同一时间基准。
     */
    private data class FailedCard(val outcome: CardOutcome, val retryAt: Long)
    private val failures = mutableMapOf<String, FailedCard>()
    private val cardCache = mutableMapOf<String, CachedCard>()
    private var cachedSignature: List<List<String>> = emptyList()

    /** 冷却到期才改变 Runner 键；连续失败至多每分钟触发一次重建。 */
    val retryRevision: Long
        get() = synchronized(lock) {
            nextRefreshAt?.let { deadline ->
                if (nanoTime() >= deadline) {
                    refreshRevision += 1
                    nextRefreshAt = nanoTime() + RETRY_COOLDOWN_NANOS
                }
            }
            refreshRevision
        }

    /** 返回当前远程子 agent；网络探测不占用缓存锁。 */
    suspend fun currentSubAgents(spec: AgentBuildSpec): List<BaseAgent> {
        val enabled = repository.currentAgents().filter { it.isEnabled }
        val signature = enabled.map { listOf(it.id, it.name, it.endpointUrl, it.bearerToken) }
        synchronized(lock) {
            if (signature != cachedSignature) {
                cardCache.clear()
                failures.clear()
                nextRefreshAt = null
                cachedSignature = signature
            }
        }
        val skipped = mutableListOf<SkippedRemoteAgent>()
        val seenNames = mutableSetOf<String>()
        val uniqueConfigs = enabled.filter { config ->
            val displayName = config.agentTreeName()
            // agent 名是 transfer_to_agent 的委派目标，必须树内唯一；重名配置后来者跳过。
            if (!seenNames.add(displayName)) {
                skipped += SkippedRemoteAgent(config.id, displayName, REASON_DUPLICATE_NAME)
                false
            } else {
                true
            }
        }
        // 独立端点并发探测，多个坏连接的等待上限不再按配置数累加；缓存写入仍在锁内串行完成。
        val outcomes = coroutineScope {
            uniqueConfigs.map { config -> async { config to resolveCard(config, signature) } }.awaitAll()
        }
        val agents = outcomes.mapNotNull { (config, outcome) ->
            val displayName = config.agentTreeName()
            if (outcome.card == null) {
                skipped += SkippedRemoteAgent(
                    remoteAgentId = config.id,
                    displayName = displayName,
                    reason = outcome.failureReason ?: REASON_UNAVAILABLE,
                )
                return@mapNotNull null
            }
            synchronized(lock) {
                if (signature == cachedSignature) cardCache[config.id] = CachedCard(config, outcome.card)
            }
            // 每次装配新建实例：同一卡片挂到多棵树/多次重建都安全。
            cancellationAwareRunCatching {
                ResilientRemoteAgent(newRemoteA2aAgent(config, outcome.card, baseHttpClient))
            }.getOrElse { failure ->
                synchronized(lock) {
                    if (signature == cachedSignature) {
                        cardCache.remove(config.id)
                        recordFailure(config, CardOutcome(null, failureSummary(failure)))
                    }
                }
                skipped += SkippedRemoteAgent(config.id, displayName, failureSummary(failure))
                null
            }
        }
        synchronized(lock) {
            if (signature == cachedSignature) nextRefreshAt = failures.values.minOfOrNull { it.retryAt }
        }
        synchronized(lock) {
            if (signature == cachedSignature) skipReporter.publish(skipped)
        }
        return agents
    }

    /** 命中缓存或拉取 agent card；网络异常归因到摘要，超时整体返回 null 原因。 */
    private suspend fun resolveCard(config: RemoteAgentConfig, signature: List<List<String>>): CardOutcome {
        synchronized(lock) {
            cardCache[config.id]?.takeIf { it.config == config }?.let { return CardOutcome(it.card, null) }
            failures[config.id]?.takeIf { nanoTime() < it.retryAt }?.let { return it.outcome }
        }
        val outcome = withTimeoutOrNull(REMOTE_AGENT_CARD_TIMEOUT_MS) {
            cancellationAwareRunCatching { cardFetcher.fetch(config) }.fold(
                onSuccess = { card -> CardOutcome(card, null) },
                onFailure = { failure -> CardOutcome(null, failureSummary(failure)) },
            )
        } ?: CardOutcome(null, null)
        synchronized(lock) {
            if (signature == cachedSignature) {
                if (outcome.card == null) recordFailure(config, outcome) else failures.remove(config.id)
            }
        }
        return outcome
    }

    private fun recordFailure(config: RemoteAgentConfig, outcome: CardOutcome) {
        val deadline = nanoTime() + RETRY_COOLDOWN_NANOS
        failures[config.id] = FailedCard(outcome, deadline)
        if (nextRefreshAt == null) nextRefreshAt = deadline
    }

    private companion object {
        const val RETRY_COOLDOWN_NANOS = 60_000_000_000L
        const val REASON_DUPLICATE_NAME: String = "duplicate agent name"
        const val REASON_UNAVAILABLE: String = "agent card unavailable"
    }
}
