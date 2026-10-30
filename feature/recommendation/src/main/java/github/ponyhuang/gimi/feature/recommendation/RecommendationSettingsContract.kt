package github.ponyhuang.gimi.feature.recommendation

import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus

/**
 * 智能推荐设置页状态。
 *
 * @property toolAccessMode 本地工具的加载方式。
 * @property enabled 是否开启推荐。
 * @property intervalHours 当前更新间隔。
 * @property intervals 可选的合法间隔。
 * @property generatedAtEpochMillis 最近成功更新时间。
 * @property refreshStatus 当前更新生命周期。
 * @property lastError 最近一次安全错误信息。
 * @property retryDelaySeconds 距下次自动重试的等待秒数；仅在失败重试等待期非空。
 * @property fastModelReady 快速模型是否已选择且当前可解析；为 false 时开启动作会被门控拦截。
 */
data class RecommendationSettingsUiState(
    val toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    val enabled: Boolean = true,
    val intervalHours: Int = 2,
    val intervals: List<Int> = listOf(1, 2, 6, 12, 24),
    val generatedAtEpochMillis: Long? = null,
    val refreshStatus: RecommendationRefreshStatus = RecommendationRefreshStatus.Idle,
    val lastError: String? = null,
    val retryDelaySeconds: Long? = null,
    val fastModelReady: Boolean = true,
)

sealed interface RecommendationSettingsAction {
    /** 修改本地工具加载方式。 */
    data class SetToolAccessMode(val mode: ToolAccessMode) : RecommendationSettingsAction
    data class SetEnabled(val enabled: Boolean) : RecommendationSettingsAction
    data class SetIntervalHours(val intervalHours: Int) : RecommendationSettingsAction
    data object RefreshNow : RecommendationSettingsAction
}

