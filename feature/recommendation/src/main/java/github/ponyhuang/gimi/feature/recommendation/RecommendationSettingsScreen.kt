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
import androidx.compose.material.icons.filled.Rule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchItem
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RecommendationSettingsScreen(
    state: RecommendationSettingsUiState,
    onAction: (RecommendationSettingsAction) -> Unit,
    onOpenFastModelSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showFastModelDialog by remember { mutableStateOf(false) }
    val a2uiLogo = ImageVector.vectorResource(R.drawable.ic_a2ui_logo)
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_settings_section)) }
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
                                Text(
                                    text = recommendationStatusText(state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = state.lastError?.let { MaterialTheme.colorScheme.error }
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_conversation_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = a2uiLogo,
                        title = stringResource(R.string.recommendation_a2ui_title),
                        subtitle = stringResource(R.string.recommendation_a2ui_subtitle),
                        checked = state.a2uiEnabled,
                        onCheckedChange = { checked ->
                            onAction(RecommendationSettingsAction.SetA2uiEnabled(checked))
                        },
                    )
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_tools_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = Icons.Default.Rule,
                        title = stringResource(R.string.recommendation_tool_loading_title),
                        subtitle = stringResource(
                            if (state.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) {
                                R.string.recommendation_tool_loading_always
                            } else {
                                R.string.recommendation_tool_loading_on_demand
                            },
                        ),
                        checked = state.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE,
                        onCheckedChange = { enabled ->
                            onAction(
                                RecommendationSettingsAction.SetToolAccessMode(
                                    if (enabled) ToolAccessMode.ALWAYS_AVAILABLE else ToolAccessMode.ON_DEMAND,
                                ),
                            )
                        },
                    )
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

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationSettingsScreenEnabledPreview() {
    AsssistantaiTheme {
        RecommendationSettingsScreen(
            state = RecommendationSettingsUiState(
                enabled = true,
                intervalHours = 2,
                generatedAtEpochMillis = Instant.parse("2026-09-06T08:30:00Z").toEpochMilli(),
                refreshStatus = RecommendationRefreshStatus.Scheduled,
            ),
            onAction = {},
            onOpenFastModelSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationSettingsScreenRetryPreview() {
    AsssistantaiTheme {
        RecommendationSettingsScreen(
            state = RecommendationSettingsUiState(
                enabled = true,
                intervalHours = 2,
                refreshStatus = RecommendationRefreshStatus.Refreshing,
                lastError = "Configure an OpenAI-compatible fast model.",
                retryDelaySeconds = 30,
            ),
            onAction = {},
            onOpenFastModelSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationSettingsScreenDisabledPreview() {
    AsssistantaiTheme {
        RecommendationSettingsScreen(
            state = RecommendationSettingsUiState(
                enabled = false,
                lastError = "权限不足，无法读取使用记录",
            ),
            onAction = {},
            onOpenFastModelSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RecommendationSettingsScreenFastModelMissingPreview() {
    // 门控态：未选择快速模型时开关可点但开启动作被拦截，展示引导对话框。
    AsssistantaiTheme {
        RecommendationSettingsScreen(
            state = RecommendationSettingsUiState(
                enabled = false,
                fastModelReady = false,
            ),
            onAction = {},
            onOpenFastModelSettings = {},
        )
        FastModelRequiredDialog(
            onConfigure = {},
            onDismiss = {},
        )
    }
}

/** 带页面外壳的固定状态预览，便于检查分组间距与系统安全区域。 */
@Composable
private fun RecommendationSettingsPagePreview(
    dark: Boolean,
    enabled: Boolean = true,
    error: Boolean = false,
    toolAccessMode: ToolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
    a2uiEnabled: Boolean = true,
) {
    AsssistantaiTheme(darkTheme = dark) {
        PreferenceScaffold(
            title = stringResource(R.string.recommendation_settings_title),
            onBack = {},
        ) { modifier ->
            RecommendationSettingsScreen(
                state = RecommendationSettingsUiState(
                    enabled = enabled,
                    toolAccessMode = toolAccessMode,
                    intervalHours = 2,
                    refreshStatus = if (error) {
                        RecommendationRefreshStatus.Refreshing
                    } else {
                        RecommendationRefreshStatus.Scheduled
                    },
                    lastError = if (error) "网络暂时不可用，请稍后重试" else null,
                    retryDelaySeconds = if (error) 30 else null,
                    a2uiEnabled = a2uiEnabled,
                ),
                onAction = {},
                onOpenFastModelSettings = {},
                modifier = modifier,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageLightPreview() {
    RecommendationSettingsPagePreview(false)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageDarkPreview() {
    RecommendationSettingsPagePreview(true)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageDisabledLightPreview() {
    RecommendationSettingsPagePreview(false, enabled = false)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageDisabledDarkPreview() {
    RecommendationSettingsPagePreview(true, enabled = false)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageErrorLightPreview() {
    RecommendationSettingsPagePreview(false, error = true)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageErrorDarkPreview() {
    RecommendationSettingsPagePreview(true, error = true)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageOnDemandLightPreview() {
    RecommendationSettingsPagePreview(false, toolAccessMode = ToolAccessMode.ON_DEMAND)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageA2uiDisabledLightPreview() {
    RecommendationSettingsPagePreview(false, a2uiEnabled = false)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageOnDemandDarkPreview() {
    RecommendationSettingsPagePreview(true, toolAccessMode = ToolAccessMode.ON_DEMAND)
}

@Preview(showBackground = true)
@Composable
fun RecommendationSettingsPageA2uiDisabledDarkPreview() {
    RecommendationSettingsPagePreview(true, a2uiEnabled = false)
}
