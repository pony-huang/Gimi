package github.ponyhuang.gimi.feature.sandbox

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingField
import github.ponyhuang.gimi.ui.preference.PreferenceFormContent
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSaveBar
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 无状态沙盒表单；配置草稿与各变量组展开态均由 ViewModel 驱动。
 * E2B 的启用开关由上一级运行环境页持有，本页只承担连接与运行配置。
 */
@Composable
fun SandboxSettingsScreen(
    state: SandboxSettingsUiState,
    onAction: (SandboxSettingsAction) -> Unit,
    onOpenDocumentation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                PreferenceSectionTitle(stringResource(R.string.sandbox_execution_section))
                PreferenceSectionNote(stringResource(R.string.sandbox_capabilities_description))
                TextButton(
                    onClick = onOpenDocumentation,
                    modifier = Modifier.padding(start = 24.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(stringResource(R.string.sandbox_documentation), style = MaterialTheme.typography.bodySmall)
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp))
                }
            }
            item {
                PreferenceSectionTitle(stringResource(R.string.sandbox_connection_section))
                PreferenceGroupCard {
                    PreferenceFormContent {
                        SandboxField(
                            state.apiUrl, { onAction(SandboxSettingsAction.SetApiUrl(it)) },
                            stringResource(R.string.sandbox_api_url),
                            stringResource(if (SandboxSettingField.API_URL in state.errors) R.string.sandbox_url_invalid else R.string.sandbox_api_url_help),
                            SandboxSettingField.API_URL in state.errors, !state.busy,
                            KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        SandboxField(
                            state.sandboxUrl, { onAction(SandboxSettingsAction.SetSandboxUrl(it)) },
                            stringResource(R.string.sandbox_proxy_url),
                            stringResource(if (SandboxSettingField.SANDBOX_URL in state.errors) R.string.sandbox_url_invalid else R.string.sandbox_proxy_url_help),
                            SandboxSettingField.SANDBOX_URL in state.errors, !state.busy,
                            KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        PreferenceTextField(
                            value = state.apiKey,
                            onValueChange = { onAction(SandboxSettingsAction.SetApiKey(it)) },
                            label = { Text(stringResource(R.string.sandbox_api_key)) },
                            supportingText = { Text(stringResource(if (SandboxSettingField.API_KEY in state.errors) R.string.sandbox_key_required else R.string.sandbox_key_help)) },
                            visualTransformation = if (state.keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { onAction(SandboxSettingsAction.ToggleKeyVisibility) }, enabled = !state.busy) {
                                    Icon(
                                        if (state.keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = stringResource(if (state.keyVisible) R.string.sandbox_hide_key else R.string.sandbox_show_key),
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = SandboxSettingField.API_KEY in state.errors,
                            enabled = !state.busy,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                PreferenceSectionTitle(stringResource(R.string.sandbox_runtime_configuration))
                PreferenceGroupCard {
                    PreferenceFormContent {
                        SandboxTemplatePicker(state, onAction)
                        SandboxField(
                            state.idleTimeoutSeconds, { onAction(SandboxSettingsAction.SetIdleTimeout(it)) },
                            stringResource(R.string.sandbox_idle_timeout),
                            stringResource(if (SandboxSettingField.IDLE_TIMEOUT in state.errors) R.string.sandbox_timeout_invalid else R.string.sandbox_timeout_help),
                            SandboxSettingField.IDLE_TIMEOUT in state.errors, !state.busy,
                            KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
            }
            item {
                PreferenceSectionTitle(stringResource(R.string.sandbox_environment))
                PreferenceSectionNote(stringResource(R.string.sandbox_environment_help))
                PreferenceGroupCard {
                    state.environment.forEachIndexed { index, entry ->
                        key(entry.id) {
                            SandboxEnvironmentGroup(entry, !state.busy,
                                showDivider = index < state.environment.lastIndex, onAction)
                        }
                    }
                }
                TextButton(
                    onClick = { onAction(SandboxSettingsAction.AddEnvironment) },
                    enabled = !state.busy,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.sandbox_add_environment), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** 与本地环境变量组保持一致的交互形态：等宽摘要行收纳展开态，值默认掩码并可切换可见性。 */
@Composable
private fun SandboxEnvironmentGroup(entry: SandboxEnvironmentDraft, enabled: Boolean, showDivider: Boolean, onAction: (SandboxSettingsAction) -> Unit) {
    val rotation by animateFloatAsState(if (entry.expanded) 180f else 0f, tween(300, easing = FastOutSlowInEasing), label = "Environment arrow")
    var valueVisible by rememberSaveable(entry.id) { mutableStateOf(false) }
    Column {
        Surface(onClick = { onAction(SandboxSettingsAction.ToggleEnvironment(entry.id)) }, enabled = enabled, color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 折叠摘要只展示名称；值可能是令牌，不应在浏览列表时直接暴露。
                Text(entry.name.ifBlank { stringResource(R.string.sandbox_new_environment) }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                Icon(Icons.Default.ExpandMore, stringResource(if (entry.expanded) R.string.sandbox_collapse_environment else R.string.sandbox_expand_environment),
                    Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SandboxExpansion(entry.expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreferenceTextField(
                    value = entry.name,
                    onValueChange = { onAction(SandboxSettingsAction.SetEnvironmentName(entry.id, it)) },
                    label = { Text(stringResource(R.string.sandbox_environment_name)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = entry.nameError,
                    supportingText = if (entry.nameError) { { Text(stringResource(R.string.sandbox_environment_invalid)) } } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                PreferenceTextField(
                    value = entry.value,
                    onValueChange = { onAction(SandboxSettingsAction.SetEnvironmentValue(entry.id, it)) },
                    label = { Text(stringResource(R.string.sandbox_environment_value)) },
                    singleLine = true,
                    enabled = enabled,
                    visualTransformation = if (valueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { valueVisible = !valueVisible }, enabled = enabled) {
                            Icon(if (valueVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                stringResource(if (valueVisible) R.string.sandbox_hide_key else R.string.sandbox_show_key))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                IconButton(onClick = { onAction(SandboxSettingsAction.RemoveEnvironment(entry.id)) }, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.sandbox_remove_environment), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun SandboxExpansion(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
    ) { content() }
}

@Composable
private fun SandboxField(value: String, onValueChange: (String) -> Unit, label: String, help: String, error: Boolean, enabled: Boolean, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    PreferenceTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, supportingText = { Text(help) },
        singleLine = true, isError = error, enabled = enabled, keyboardOptions = keyboardOptions, modifier = Modifier.fillMaxWidth())
}


/** 保存区属于页面壳，不随表单滚动；保持连接草稿必须显式保存的语义。 */
@Composable
internal fun SandboxSettingsSaveBar(state: SandboxSettingsUiState, onAction: (SandboxSettingsAction) -> Unit) {
    PreferenceSaveBar(
        label = stringResource(if (state.busy) R.string.sandbox_saving else R.string.sandbox_save),
        onSave = { onAction(SandboxSettingsAction.Save) },
        enabled = state.canSave,
        notice = if (state.dirty) stringResource(R.string.sandbox_unsaved_changes) else null,
    )
}

@Preview(name = "Configured light", widthDp = 390, heightDp = 780)
@Preview(name = "Configured dark", widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxConfiguredPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configured = true, apiKey = "preview-key"))

@Preview(name = "Runtime light", widthDp = 390, heightDp = 1080)
@Preview(name = "Runtime dark", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Runtime tablet", widthDp = 840, heightDp = 1080)
@Composable
private fun SandboxRuntimePreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configured = true, apiKey = "preview-key",
    environment = listOf(SandboxEnvironmentDraft(0, "LANG", "zh_CN.UTF-8"), SandboxEnvironmentDraft(1, expanded = true)), dirty = true))

@Preview(name = "Unconfigured", widthDp = 390, heightDp = 780)
@Composable
private fun SandboxUnconfiguredPreview() = SandboxScreenPreview(SandboxSettingsUiState())

@Preview(name = "Invalid fields", widthDp = 320, heightDp = 780, fontScale = 1.3f)
@Composable
private fun SandboxInvalidPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, apiUrl = "192.168.1.10:3000", errors = setOf(SandboxSettingField.API_URL, SandboxSettingField.API_KEY)))

@Preview(name = "Local HTTP light", widthDp = 390, heightDp = 1080)
@Preview(name = "Local HTTP dark", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxLocalHttpPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true,
    apiUrl = "http://192.168.1.10:3000", sandboxUrl = "http://192.168.1.10:3002", apiKey = "preview-key", template = "base"))

@Composable
private fun SandboxScreenPreview(state: SandboxSettingsUiState) {
    GimiTheme {
        PreferenceScaffold(title = stringResource(R.string.sandbox_screen_title), onBack = {}, style = PreferenceStyle.Compact, bottomBar = { SandboxSettingsSaveBar(state, {}) }) {
            SandboxSettingsScreen(state, {}, {}, it)
        }
    }
}
