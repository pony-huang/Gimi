package github.ponyhuang.gimi.feature.memory

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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

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
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Psychology,
                        title = stringResource(R.string.memory_enabled_title),
                        subtitle = stringResource(R.string.memory_enabled_subtitle),
                        trailingContent = {
                            Switch(
                                checked = state.memoryEnabled,
                                onCheckedChange = {
                                    onAction(MemorySettingsAction.SetMemoryEnabled(it))
                                },
                            )
                        },
                        onClick = {
                            onAction(MemorySettingsAction.SetMemoryEnabled(!state.memoryEnabled))
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
                    PreferenceListItem(
                        icon = mem0Icon,
                        iconContainer = Color.Transparent,
                        iconTint = Color.Unspecified,
                        iconSize = 34.dp,
                        title = stringResource(R.string.memory_mem0_enabled_title),
                        subtitle = stringResource(R.string.memory_mem0_enabled_subtitle),
                        showDivider = state.mem0Enabled,
                        trailingContent = {
                            Switch(
                                checked = state.mem0Enabled,
                                enabled = state.memoryEnabled,
                                onCheckedChange = {
                                    onAction(MemorySettingsAction.SetMem0Enabled(it))
                                },
                            )
                        },
                        onClick = {
                            if (state.memoryEnabled) {
                                onAction(MemorySettingsAction.SetMem0Enabled(!state.mem0Enabled))
                            }
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
                                iconSize = 34.dp,
                                title = stringResource(R.string.memory_history_title),
                                subtitle = stringResource(R.string.memory_history_subtitle),
                                modifier = Modifier.alpha(
                                    if (state.hasStoredToken) ENABLED_HISTORY_ALPHA else DISABLED_HISTORY_ALPHA,
                                ),
                                showDivider = true,
                                onClick = onNavigateToHistory.takeIf { state.hasStoredToken },
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedTextField(
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
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Text(
                                        text = stringResource(
                                            if (state.saving) R.string.memory_saving else R.string.memory_save,
                                        ),
                                        modifier = Modifier.padding(start = 8.dp),
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

private const val ENABLED_HISTORY_ALPHA = 1f
private const val DISABLED_HISTORY_ALPHA = 0.5f

@Preview(showBackground = true)
@Composable
private fun MemorySettingsScreenPreview() {
    AsssistantaiTheme {
        MemorySettingsScreen(
            state = MemorySettingsUiState(hasStoredToken = true),
            onAction = {},
            onNavigateToHistory = {},
            onOpenMem0Quickstart = {},
        )
    }
}
