package github.ponyhuang.gimi.feature.recommendation

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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationRefreshStatus
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun RecommendationSettingsScreen(
    state: RecommendationSettingsUiState,
    onAction: (RecommendationSettingsAction) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIntervalDialog by remember { mutableStateOf(false) }
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_settings_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.AutoAwesome,
                        title = stringResource(R.string.recommendation_enabled_title),
                        subtitle = stringResource(R.string.recommendation_enabled_subtitle),
                        showDivider = state.enabled,
                        trailingContent = {
                            Switch(
                                checked = state.enabled,
                                onCheckedChange = {
                                    onAction(RecommendationSettingsAction.SetEnabled(it))
                                },
                            )
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
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = recommendationStatusText(state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = state.lastError?.let { MaterialTheme.colorScheme.error }
                                        ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = { onAction(RecommendationSettingsAction.RefreshNow) },
                                    enabled = state.enabled &&
                                        state.refreshStatus == RecommendationRefreshStatus.Idle,
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .align(Alignment.CenterHorizontally)
                                        .testTag("recommendation_refresh_action"),
                                ) {
                                    Text(text = stringResource(R.string.recommendation_refresh_now))
                                }
                            }
                        }
                    }
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.recommendation_privacy_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Apps,
                        title = stringResource(R.string.recommendation_permissions_title),
                        subtitle = stringResource(R.string.recommendation_permissions_subtitle),
                        onClick = onOpenPermissions,
                    )
                }
            }
        }
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
