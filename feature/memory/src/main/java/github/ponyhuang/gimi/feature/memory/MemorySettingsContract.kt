package github.ponyhuang.gimi.feature.memory

/**
 * 记忆设置页状态。
 *
 * @property memoryEnabled 记忆总开关。
 * @property mem0Enabled 是否启用 Mem0。
 * @property token 当前 Token，由密码输入框负责默认遮罩。
 * @property hasStoredToken 是否已有安全保存的 Token。
 * @property tokenError Token 是否缺失。
 * @property saving 是否正在保存。
 */
data class MemorySettingsUiState(
    val memoryEnabled: Boolean = true,
    val mem0Enabled: Boolean = false,
    val token: String = "",
    val hasStoredToken: Boolean = false,
    val tokenError: Boolean = false,
    val saving: Boolean = false,
)

sealed interface MemorySettingsAction {
    /** 切换记忆总开关。 */
    data class SetMemoryEnabled(val enabled: Boolean) : MemorySettingsAction

    /** 切换 Mem0 后端。 */
    data class SetMem0Enabled(val enabled: Boolean) : MemorySettingsAction

    /** 修改待保存 Token。 */
    data class SetToken(val token: String) : MemorySettingsAction

    /** 保存当前设置。 */
    data object Save : MemorySettingsAction
}

sealed interface MemorySettingsEffect {
    /** 配置已经安全保存。 */
    data object Saved : MemorySettingsEffect

    /** 安全存储写入失败。 */
    data object SaveFailed : MemorySettingsEffect
}
