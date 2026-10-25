package github.ponyhuang.gimi.feature.a2a

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentCardSnapshot
import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig

/** 编辑器表单草稿；与配置模型解耦，字段均为原始输入（保存时统一裁剪）。 */
data class RemoteAgentEditorDraft(
    val id: String,
    val isNew: Boolean,
    val name: String,
    val endpointUrl: String,
    val bearerToken: String,
    val isEnabled: Boolean,
)

/**
 * 远程 A2A agent 配置页的不可变状态。
 *
 * @property agents 已保存的远程 agent 配置。
 * @property editor 当前编辑草稿；未进入编辑器时为 null。
 * @property editorCard 编辑器内展示的卡片快照；进入编辑器时取自已保存配置，
 *   点击测试连接后更新为最新探测结果。
 * @property isMutationBlocked agent 运行中的变更门控，开启时禁止一切写操作。
 * @property isTestingConnection 保存前连通性探测进行中。
 * @property connectionError 最近一次探测/保存失败的摘要键或原文。
 * @property menuServerId 正在显示操作菜单的配置 ID。
 */
data class A2aSettingsUiState(
    val agents: List<RemoteAgentConfig> = emptyList(),
    val editor: RemoteAgentEditorDraft? = null,
    val editorCard: RemoteAgentCardSnapshot? = null,
    val isMutationBlocked: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionError: String? = null,
    val menuServerId: String? = null,
)

sealed interface A2aSettingsAction {
    data class ToggleAgent(val agent: RemoteAgentConfig, val enabled: Boolean) : A2aSettingsAction

    /** 打开或关闭某个配置的操作菜单。 */
    data class AgentMenuChanged(val agentId: String?) : A2aSettingsAction
    data class LoadEditor(val agentId: String?) : A2aSettingsAction
    data class EditorChanged(val draft: RemoteAgentEditorDraft) : A2aSettingsAction

    /** 用当前草稿的地址做一次连通性探测，成功后刷新编辑器卡片详情。 */
    data object TestConnection : A2aSettingsAction
    data object SaveEditor : A2aSettingsAction
    data object DeleteEditor : A2aSettingsAction
}

/** 一次性 UI 反馈（关闭页面等），由 Route 经 effects 通道消费。 */
sealed interface A2aSettingsEffect {
    /** 删除成功，请求关闭当前编辑页。 */
    data object Close : A2aSettingsEffect

    /** 保存成功，提示用户后关闭编辑页。 */
    data object Saved : A2aSettingsEffect
}

internal fun RemoteAgentConfig.toDraft(isNew: Boolean) = RemoteAgentEditorDraft(
    id = id,
    isNew = isNew,
    name = name,
    endpointUrl = endpointUrl,
    bearerToken = bearerToken,
    isEnabled = isEnabled,
)

internal fun RemoteAgentEditorDraft.toAgent() = RemoteAgentConfig(
    id = id,
    name = name.trim(),
    endpointUrl = endpointUrl.trim(),
    bearerToken = bearerToken.trim(),
    isEnabled = isEnabled,
)
