package github.ponyhuang.gimi.data.agent.a2a

import com.google.adk.kt.a2a.jvm.A2AAgent
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentCardSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentSkillSnapshot
import org.a2aproject.sdk.client.http.A2AHttpClient
import org.a2aproject.sdk.spec.AgentCard

/** 远程 A2A agent 的公共装配参数：卡片拉取超时与网络异常摘要截取。 */

/** 卡片拉取/探测的统一超时上限；超时按不可达处理，不做无限等待。 */
internal const val REMOTE_AGENT_CARD_TIMEOUT_MS: Long = 15_000L

/** 配置对应的 agent 树内名称；用户未命名时以稳定 id 兜底，保证委派目标可用。 */
internal fun RemoteAgentConfig.agentTreeName(): String = name.ifBlank { id }

/** 命中 Bearer 凭据时以 Authorization 头装饰共享的 Android 传输实现。 */
internal fun RemoteAgentConfig.httpClientFor(baseHttpClient: A2AHttpClient): A2AHttpClient =
    if (bearerToken.isBlank()) {
        baseHttpClient
    } else {
        StaticHeaderA2AHttpClient(
            delegate = baseHttpClient,
            headerName = "Authorization",
            headerValue = "Bearer $bearerToken",
        )
    }

/**
 * 由已解析的卡片新建 agent 实例。每次 agent 树装配都须调用本函数取得全新实例，
 * 绝不能复用此前挂过树的实例。
 */
internal fun newRemoteA2aAgent(
    config: RemoteAgentConfig,
    agentCard: AgentCard,
    baseHttpClient: A2AHttpClient,
): com.google.adk.kt.a2a.agent.BaseRemoteA2AAgent =
    A2AAgent(
        name = config.agentTreeName(),
        agentCard = agentCard,
        httpClient = config.httpClientFor(baseHttpClient),
    )

/** 把异常归约为单行失败摘要；不落盘、不含凭据，供跳过上报与编辑器错误展示共用。 */
internal fun failureSummary(failure: Throwable): String? =
    failure.message?.lineSequence()?.firstOrNull()

/**
 * 裁剪卡片中用户可读的字段形成快照；协议版本取自接口声明，多接口时保留首个。
 * 安全方案、签名等敏感或非展示字段不进入快照。
 */
internal fun AgentCard.toSnapshot(): RemoteAgentCardSnapshot = RemoteAgentCardSnapshot(
    name = name,
    version = version,
    protocolVersion = supportedInterfaces?.firstOrNull()?.protocolVersion,
    description = description,
    providerOrganization = provider?.organization,
    providerUrl = provider?.url,
    documentationUrl = documentationUrl,
    supportsStreaming = capabilities?.streaming ?: false,
    supportsPushNotifications = capabilities?.pushNotifications ?: false,
    supportsExtendedAgentCard = capabilities?.extendedAgentCard ?: false,
    extensionUris = capabilities?.extensions.orEmpty().mapNotNull { it.uri },
    skills = skills.orEmpty().map {
        RemoteAgentSkillSnapshot(
            id = it.id,
            name = it.name,
            description = it.description,
            tags = it.tags.orEmpty(),
        )
    },
    defaultInputModes = defaultInputModes.orEmpty(),
    defaultOutputModes = defaultOutputModes.orEmpty(),
    preferredTransport = preferredTransport,
)
