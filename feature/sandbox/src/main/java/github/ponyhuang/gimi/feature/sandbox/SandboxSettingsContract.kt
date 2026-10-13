package github.ponyhuang.gimi.feature.sandbox

import github.ponyhuang.gimi.domain.sandbox.SandboxSettingField
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplate

/** 模板列表的业务加载状态，错误由 Screen 映射为本地化文案。 */
enum class SandboxTemplateLoadState { IDLE, LOADING, LOADED, CREDENTIALS_REQUIRED, AUTHENTICATION, RATE_LIMIT, NETWORK, PROTOCOL, UNAVAILABLE }

/**
 * 环境变量编辑行；id 在编辑与保存时保持稳定，nameError 表示名称无效或重复。
 * @property expanded 只控制该组的编辑区展示；已有变量默认折叠，新增或校验失败时展开。
 */
data class SandboxEnvironmentDraft(
    val id: Long,
    val name: String = "",
    val value: String = "",
    val nameError: Boolean = false,
    val expanded: Boolean = false,
)

/**
 * 沙盒页唯一展示状态；连接字段是草稿，开关和 configured 来自成功保存的设置。
 * @property idleTimeoutSeconds 秒数字符串，保留输入中的空值以提供校验反馈。
 * @property busy 正在提交设置，期间禁止修改连接字段或开关。
 * @property dirty 草稿是否存在未保存的修改。
 * @property errors 可定位到输入框的业务校验错误。
 * @property templates 当前草稿凭据查询到的完整列表，仅保存在页面状态中。
 * @property templateLoadState 模板查询状态，与配置保存的 busy 独立。
 */
data class SandboxSettingsUiState(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val domain: String = "e2b.app",
    val apiKey: String = "",
    val template: String = "code-interpreter-v1",
    val idleTimeoutSeconds: String = "300",
    val environment: List<SandboxEnvironmentDraft> = emptyList(),
    val advancedExpanded: Boolean = false,
    val keyVisible: Boolean = false,
    val busy: Boolean = false,
    val dirty: Boolean = false,
    val errors: Set<SandboxSettingField> = emptySet(),
    val templates: List<SandboxTemplate> = emptyList(),
    val templateLoadState: SandboxTemplateLoadState = SandboxTemplateLoadState.IDLE,
) {
    /** 即时开关独立于草稿保存；已配置且无修改时不重复提交。 */
    val canSave: Boolean get() = enabled && !busy && (dirty || !configured)
    override fun toString(): String = "SandboxSettingsUiState(enabled=$enabled, configured=$configured, busy=$busy, credentials=<redacted>)"
}

/** 设置页意图；仅显式加载和刷新模板触发网络查询。 */
sealed interface SandboxSettingsAction {
    /** 即时保存 E2B 开关，保留已保存连接参数。 */
    data class SetEnabled(val enabled: Boolean) : SandboxSettingsAction
    /** 修改基础域名草稿。 */
    data class SetDomain(val value: String) : SandboxSettingsAction
    /** 修改 API 密钥草稿。 */
    data class SetApiKey(val value: String) : SandboxSettingsAction
    /** 修改模板名称或 ID 草稿。 */
    data class SetTemplate(val value: String) : SandboxSettingsAction
    /** 首次展开下拉框时查询当前凭据的模板。 */
    data object LoadTemplates : SandboxSettingsAction
    /** 显式重新查询模板，不保存连接草稿。 */
    data object RefreshTemplates : SandboxSettingsAction
    /** 修改闲置超时的数字输入。 */
    data class SetIdleTimeout(val value: String) : SandboxSettingsAction
    /** 修改指定环境变量行名称。 */
    data class SetEnvironmentName(val id: Long, val value: String) : SandboxSettingsAction
    /** 修改指定环境变量行值。 */
    data class SetEnvironmentValue(val id: Long, val value: String) : SandboxSettingsAction
    /** 删除指定编辑行。 */
    data class RemoveEnvironment(val id: Long) : SandboxSettingsAction
    /** 展开或收起指定环境变量组，不修改配置草稿。 */
    data class ToggleEnvironment(val id: Long) : SandboxSettingsAction
    /** 添加允许空值的环境变量编辑行。 */
    data object AddEnvironment : SandboxSettingsAction
    /** 展开或折叠高级配置。 */
    data object ToggleAdvanced : SandboxSettingsAction
    /** 切换密钥遮罩，仅影响展示。 */
    data object ToggleKeyVisibility : SandboxSettingsAction
    /** 校验并保存连接草稿。 */
    data object Save : SandboxSettingsAction
}

/** Route 消费的用户通知，不持久化。 */
enum class SandboxSettingsEffect { SAVED, SAVE_FAILED }
