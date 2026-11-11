package github.ponyhuang.gimi.feature.recommendation

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchItem
import github.ponyhuang.gimi.ui.theme.GimiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * 「推荐更新」独立设置页：承载智能推荐开关、更新间隔与手动更新。
 *
 * 卡片内的行与按钮沿用原「个性化选项」页推荐分组的排布，
 * 唯一变化是把状态/失败提示从卡片内部提到分组标题与卡片之间的小字区域，
 * 使更新失败原因在进入页面时就能看到，而不必先滚动到卡片底部。
 */
@Composable
fun RecommendationUpdateSettingsScreen(
    state: RecommendationSettingsUiState,
    onAction: (RecommendationSettingsAction) -> Unit,
    onOpenFastModelSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showFastModelDialog by remember { mutableStateOf(false) }
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_settings_section)) }
            // 状态提示与标题同属一组的小字区域，失败时用错误色，其余状态保持次要文字色。
            item {
                AnimatedVisibility(
                    visible = state.enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    PreferenceSectionNote(
                        text = recommendationStatusText(state),
                        color = state.lastError?.let { MaterialTheme.colorScheme.error }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.recommendation_enabled_title),
                        subtitle = stringResource(R.string.recommendation_enabled_subtitle),
                        showDivider = state.enabled,
                        checked = state.enabled,
                        onCheckedChange = { checked ->
                            // 推荐由快速模型生成：未配置时不写入开启状态，先引导去模型设置。
                            if (checked && !state.fastModelReady) {
                                showFastModelDialog = true
                            } else {
                                onAction(RecommendationSettingsAction.SetEnabled(checked))
                            }
                        },
                    )
                    // 更新相关配置仅在推荐开启后展开，关闭时整块隐藏。
                    AnimatedVisibility(
                        visible = state.enabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            PreferenceListItem(
                                icon = Icons.Default.Schedule,
                                title = stringResource(R.string.recommendation_interval_title),
                                subtitle = stringResource(
                                    R.string.recommendation_interval_value,
                                    state.intervalHours,
                                ),
                                showDivider = true,
                                onClick = { showIntervalDialog = true },
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = { onAction(RecommendationSettingsAction.RefreshNow) },
                                    // 失败重试等待期也是用户最可能想手动重试的时刻：只要没有正在进行的
                                    // 模型调用（无 lastError 的 Refreshing），就允许立即更新插队。
                                    enabled = state.enabled &&
                                        (state.refreshStatus == RecommendationRefreshStatus.Idle ||
                                            state.lastError != null),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("recommendation_refresh_action"),
                                ) {
                                    Text(text = stringResource(R.string.recommendation_refresh_now))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showFastModelDialog) {
        FastModelRequiredDialog(
            onConfigure = {
                showFastModelDialog = false
                onOpenFastModelSettings()
            },
            onDismiss = { showFastModelDialog = false },
        )
    }
    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.recommendation_interval_title)) },
            text = {
                Column {
                    state.intervals.forEach { hours ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = hours == state.intervalHours,
                                onClick = {
                                    onAction(RecommendationSettingsAction.SetIntervalHours(hours))
                                    showIntervalDialog = false
                                },
                            )
                            Text(stringResource(R.string.recommendation_interval_value, hours))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.recommendation_close))
                }
            },
        )
    }
}

/** 未配置快速模型时的开启引导：解释前置条件并提供直达模型设置的入口。 */
@Composable
private fun FastModelRequiredDialog(
    onConfigure: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recommendation_fast_model_required_title)) },
        text = { Text(stringResource(R.string.recommendation_fast_model_required_text)) },
        confirmButton = {
            TextButton(onClick = onConfigure) {
                Text(stringResource(R.string.recommendation_fast_model_required_configure))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.recommendation_close))
            }
        },
    )
}

@Composable
private fun recommendationStatusText(state: RecommendationSettingsUiState): String = when {
    state.lastError != null && state.retryDelaySeconds != null ->
        stringResource(
            R.string.recommendation_status_retrying,
            state.lastError,
            state.retryDelaySeconds,
        )
    state.lastError != null -> stringResource(R.string.recommendation_status_error, state.lastError)
    state.refreshStatus == RecommendationRefreshStatus.Refreshing ->
        stringResource(R.string.recommendation_status_refreshing)
    state.refreshStatus == RecommendationRefreshStatus.Scheduled ->
        stringResource(R.string.recommendation_status_scheduled)
    state.generatedAtEpochMillis == null -> stringResource(R.string.recommendation_status_never)
    else -> {
        val formatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .format(
                Instant.ofEpochMilli(state.generatedAtEpochMillis)
                    .atZone(ZoneId.systemDefault()),
            )
        stringResource(R.string.recommendation_status_updated, formatted)
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationUpdateSettingsPagePreview() {
    GimiTheme {
        PreferenceScaffold(
            style = PreferenceStyle.Compact,
            title = stringResource(R.string.recommendation_update_page_title),
            onBack = {},
        ) { modifier ->
            RecommendationUpdateSettingsScreen(
                state = RecommendationSettingsUiState(
                    enabled = true,
                    intervalHours = 2,
                    generatedAtEpochMillis = Instant.parse("2026-09-06T08:30:00Z").toEpochMilli(),
                    refreshStatus = RecommendationRefreshStatus.Scheduled,
                ),
                onAction = {},
                onOpenFastModelSettings = {},
                modifier = modifier,
            )
        }
    }
}

@Preview(name = "Phone-更新失败", device = Devices.PHONE, showBackground = true)
@Composable
private fun RecommendationUpdateSettingsErrorPagePreview() {
    GimiTheme {
        PreferenceScaffold(
            style = PreferenceStyle.Compact,
            title = stringResource(R.string.recommendation_update_page_title),
            onBack = {},
        ) { modifier ->
            RecommendationUpdateSettingsScreen(
                state = RecommendationSettingsUiState(
                    enabled = true,
                    intervalHours = 2,
                    refreshStatus = RecommendationRefreshStatus.Refreshing,
                    lastError = "Configure an OpenAI-compatible fast model for recommendation generation.",
                    retryDelaySeconds = 30,
                ),
                onAction = {},
                onOpenFastModelSettings = {},
                modifier = modifier,
            )
        }
    }
}

@Preview(name = "Phone-未开启", device = Devices.PHONE, showBackground = true)
@Composable
private fun RecommendationUpdateSettingsDisabledPagePreview() {
    GimiTheme {
        PreferenceScaffold(
            style = PreferenceStyle.Compact,
            title = stringResource(R.string.recommendation_update_page_title),
            onBack = {},
        ) { modifier ->
            RecommendationUpdateSettingsScreen(
                state = RecommendationSettingsUiState(
                    enabled = false,
                    fastModelReady = false,
                ),
                onAction = {},
                onOpenFastModelSettings = {},
                modifier = modifier,
            )
        }
    }
}
