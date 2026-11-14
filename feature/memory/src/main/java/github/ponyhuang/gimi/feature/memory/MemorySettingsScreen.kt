package github.ponyhuang.gimi.feature.memory

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchRow
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.GimiTheme

@Composable
fun MemorySettingsScreen(
    state: MemorySettingsUiState,
    onAction: (MemorySettingsAction) -> Unit,
    onNavigateToHistory: () -> Unit,
    onOpenMem0Quickstart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mem0Icon = ImageVector.vectorResource(R.drawable.ic_mem0)
    var tokenVisible by remember { mutableStateOf(false) }
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceGroupCard {
                    PreferenceSwitchRow(
                        icon = Icons.Default.Psychology,
                        title = stringResource(R.string.memory_enabled_title),
                        description = stringResource(R.string.memory_enabled_subtitle),
                        checked = state.memoryEnabled,
                        onCheckedChange = {
                            onAction(MemorySettingsAction.SetMemoryEnabled(it))
                        },
                    )
                }
            }
            item {
                Column {
                    PreferenceSectionTitle(stringResource(R.string.memory_section_provider))
                    Text(
                        text = stringResource(R.string.memory_mem0_quickstart),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clickable(onClick = onOpenMem0Quickstart)
                            .padding(vertical = 2.dp),
                    )
                }
            }
            item {
                PreferenceGroupCard {
                    PreferenceSwitchRow(
                        // Mem0 是多彩品牌图标，必须关闭 Icon 默认的单色 tint，
                        // 否则整个 logo 会被 LocalContentColor 染成纯色方块。
                        leadingIcon = {
                            Icon(
                                mem0Icon,
                                contentDescription = null,
                                modifier = it,
                                tint = Color.Unspecified,
                            )
                        },
                        title = stringResource(R.string.memory_mem0_enabled_title),
                        description = stringResource(R.string.memory_mem0_enabled_subtitle),
                        showDivider = state.mem0Enabled,
                        checked = state.mem0Enabled,
                        enabled = state.memoryEnabled,
                        onCheckedChange = {
                            onAction(MemorySettingsAction.SetMem0Enabled(it))
                        },
                    )
                    // Token 配置仅在 Mem0 开启后展开，关闭时整块隐藏。
                    AnimatedVisibility(
                        visible = state.mem0Enabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column {
                            PreferenceListItem(
                                icon = mem0Icon,
                                iconContainer = Color.Transparent,
                                iconTint = Color.Unspecified,
                                iconSize = 24.dp,
                                title = stringResource(R.string.memory_history_title),
                                subtitle = stringResource(R.string.memory_history_subtitle),
                                // 没有 token 时该行不可用：只降级文字颜色，不用整行透明度，
                                // 避免把品牌图标和描边一起冲淡。
                                titleColor = if (state.hasStoredToken) Color.Unspecified
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                subtitleColor = if (state.hasStoredToken) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                showDivider = true,
                                onClick = onNavigateToHistory.takeIf { state.hasStoredToken },
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PreferenceTextField(
                                    value = state.token,
                                    onValueChange = { onAction(MemorySettingsAction.SetToken(it)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.memory_token_label)) },
                                    singleLine = true,
                                    enabled = state.memoryEnabled,
                                    isError = state.tokenError,
                                    supportingText = if (state.tokenError) {
                                        { Text(stringResource(R.string.memory_token_required)) }
                                    } else if (state.hasStoredToken) {
                                        { Text(stringResource(R.string.memory_token_stored_placeholder)) }
                                    } else {
                                        null
                                    },
                                    visualTransformation = if (tokenVisible) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                            Icon(
                                                imageVector = if (tokenVisible) {
                                                    Icons.Default.VisibilityOff
                                                } else {
                                                    Icons.Default.Visibility
                                                },
                                                contentDescription = stringResource(
                                                    if (tokenVisible) {
                                                        R.string.memory_hide_token
                                                    } else {
                                                        R.string.memory_show_token
                                                    },
                                                ),
                                            )
                                        }
                                    },
                                )
                                Button(
                                    onClick = { onAction(MemorySettingsAction.Save) },
                                    enabled = !state.saving && state.memoryEnabled,
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .align(Alignment.CenterHorizontally)
                                        .testTag("memory_save_action"),
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (state.saving) R.string.memory_saving else R.string.memory_save,
                                        ),
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MemorySettingsScreenPreview() {
    GimiTheme {
        MemorySettingsScreen(
            state = MemorySettingsUiState(hasStoredToken = true),
            onAction = {},
            onNavigateToHistory = {},
            onOpenMem0Quickstart = {},
        )
    }
}
