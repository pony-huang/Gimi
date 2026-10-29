package github.ponyhuang.gimi.domain.a2a.model

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 已配置的远程 A2A agent 及用户提供的连接信息。
 *
 * 一个配置对应 agent 树中的一个 sub-agent：agent 构建期按 [endpointUrl] 拉取
 * A2A agent card 并建立 JSON-RPC 客户端，会话中由模型经 `transfer_to_agent` 委派。
 *
 * @property id 稳定唯一标识，与 agent 树装配、跳过上报关联。
 * @property name 用户可见名称，同时作为 ADK agent 树内的 agent 名（委派目标）。
 * @property endpointUrl 远程 agent 的基础地址或完整 agent card 地址；
 *   构建期自动补齐 `/.well-known/agent-card.json` 标准路径。
 * @property bearerToken 可选 Bearer 凭据，命中时以 `Authorization` 头注入全部请求；
 *   空表示匿名访问。
 * @property isEnabled 关闭后不参与 agent 树装配，配置本身保留。
 * @property cardSnapshot 最近一次探测成功时裁剪出的 agent card 快照，供编辑器展示；
 *   从未成功探测过时为 null。
 */
@Serializable
data class RemoteAgentConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val endpointUrl: String = "",
    val bearerToken: String = "",
    val isEnabled: Boolean = true,
    val cardSnapshot: RemoteAgentCardSnapshot? = null,
)

/**
 * Agent card 中可展示字段的裁剪快照；只保留用户可读信息，不含安全方案与签名。
 *
 * @property name 卡片声明的官方 agent 名称。
 * @property version 卡片版本号。
 * @property protocolVersion 卡片主接口声明的协议版本（如 "1.0"）。
 * @property description 卡片对 agent 能力的整体描述。
 * @property providerOrganization 提供方组织名。
 * @property providerUrl 提供方链接。
 * @property documentationUrl 文档链接。
 * @property supportsStreaming 能力位：支持流式响应。
 * @property supportsPushNotifications 能力位：支持推送通知。
 * @property supportsExtendedAgentCard 能力位：支持认证扩展卡片。
 * @property extensionUris 声明的扩展 URI 列表。
 * @property skills 卡片声明的技能列表。
 * @property defaultInputModes 默认输入 MIME 类型。
 * @property defaultOutputModes 默认输出 MIME 类型。
 * @property preferredTransport 首选传输方式（如 "JSONRPC"）。
 */
@Serializable
data class RemoteAgentCardSnapshot(
    val name: String? = null,
    val version: String? = null,
    val protocolVersion: String? = null,
    val description: String? = null,
    val providerOrganization: String? = null,
    val providerUrl: String? = null,
    val documentationUrl: String? = null,
    val supportsStreaming: Boolean = false,
    val supportsPushNotifications: Boolean = false,
    val supportsExtendedAgentCard: Boolean = false,
    val extensionUris: List<String> = emptyList(),
    val skills: List<RemoteAgentSkillSnapshot> = emptyList(),
    val defaultInputModes: List<String> = emptyList(),
    val defaultOutputModes: List<String> = emptyList(),
    val preferredTransport: String? = null,
)

/**
 * Agent card 单个技能的可展示字段。
 *
 * @property id 技能稳定标识。
 * @property name 技能展示名。
 * @property description 技能能力描述。
 * @property tags 技能分类标签。
 */
@Serializable
data class RemoteAgentSkillSnapshot(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

/**
 * Agent 构建期被判定不可达而跳过的远程 A2A agent。
 *
 * @property remoteAgentId 对应 [RemoteAgentConfig.id]。
 * @property displayName 对应 [RemoteAgentConfig.name]，供 UI 直接展示。
 * @property reason 失败原因摘要（HTTP 状态、解析失败等），不含认证凭据。
 */
data class SkippedRemoteAgent(
    val remoteAgentId: String,
    val displayName: String,
    val reason: String? = null,
)

/**
 * 远程 A2A agent 连通性探测结果。
 *
 * @property reachable 卡片可拉取且解析通过。
 * @property errorMessage 失败摘要（HTTP 状态、解析失败等），不含认证凭据；为 null 时
 *   调用方应展示本地化默认文案。
 * @property card 探测成功时拉取到的卡片快照；失败时为 null。
 */
data class RemoteAgentProbeResult(
    val reachable: Boolean,
    val errorMessage: String? = null,
    val card: RemoteAgentCardSnapshot? = null,
)
