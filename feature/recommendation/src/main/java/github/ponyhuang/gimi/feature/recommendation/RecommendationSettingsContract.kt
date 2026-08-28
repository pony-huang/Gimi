package github.ponyhuang.gimi.feature.recommendation

import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus

/**
 * 智能推荐设置页状态。
 *
 * @property enabled 是否开启推荐。
 * @property intervalHours 当前更新间隔。
 * @property intervals 可选的合法间隔。
 * @property generatedAtEpochMillis 最近成功更新时间。
 * @property refreshStatus 当前更新生命周期。
 * @property lastError 最近一次安全错误信息。
 */
data class RecommendationSettingsUiState(
    val enabled: Boolean = true,
    val intervalHours: Int = 2,
    val intervals: List<Int> = listOf(1, 2, 6, 12, 24),
    val generatedAtEpochMillis: Long? = null,
    val refreshStatus: RecommendationRefreshStatus = RecommendationRefreshStatus.Idle,
    val lastError: String? = null,
)

sealed interface RecommendationSettingsAction {
    data class SetEnabled(val enabled: Boolean) : RecommendationSettingsAction
    data class SetIntervalHours(val intervalHours: Int) : RecommendationSettingsAction
    data object RefreshNow : RecommendationSettingsAction
}

