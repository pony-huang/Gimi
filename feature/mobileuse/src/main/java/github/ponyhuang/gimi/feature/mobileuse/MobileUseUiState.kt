package github.ponyhuang.gimi.feature.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability

/**
 * Shizuku 授权与后台操作的页面状态。
 * @property availability 功能开关、Shizuku 授权及任务的当前状态。
 * @property textInputAvailable 文字输入无障碍服务是否已启用。
 */
data class MobileUseUiState(
    val availability: MobileUseAvailability = MobileUseAvailability.DISABLED,
    val textInputAvailable: Boolean = false,
)
