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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 无状态沙盒表单；即时开关、配置草稿与各变量组展开态均由 ViewModel 驱动。 */
@Composable
fun SandboxSettingsScreen(
    state: SandboxSettingsUiState,
    onAction: (SandboxSettingsAction) -> Unit,
    onOpenDocumentation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configurationRotation by animateFloatAsState(
        if (state.configurationExpanded) 180f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "Configuration arrow",
    )
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                PreferenceSectionTitle(stringResource(R.string.sandbox_execution_section))
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.sandbox_e2b),
                        subtitle = stringResource(when {
                            !state.enabled -> R.string.sandbox_disabled_description
                            !state.configured -> R.string.sandbox_pending_description
                            else -> R.string.sandbox_configured_description
                        }),
                        onClick = if (state.busy) null else { { onAction(SandboxSettingsAction.ToggleConfiguration) } },
                        trailingContent = {
                            Switch(
                                checked = state.enabled,
                                enabled = !state.busy,
                                onCheckedChange = { onAction(SandboxSettingsAction.SetEnabled(it)) },
                            )
                            IconButton(
                                onClick = { onAction(SandboxSettingsAction.ToggleConfiguration) },
                                enabled = !state.busy,
                            ) {
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = stringResource(if (state.configurationExpanded) R.string.sandbox_collapse_configuration else R.string.sandbox_expand_configuration),
                                    modifier = Modifier.rotate(configurationRotation),
                                )
                            }
                        },
                    )
                }
            }
            item {
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
                SandboxExpansion(state.configurationExpanded) {
                    Column {
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
                        PreferenceSectionTitle(stringResource(R.string.sandbox_environment))
                        PreferenceGroupCard {
                            PreferenceFormContent {
                                Text(stringResource(R.string.sandbox_environment_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                state.environment.forEach { entry ->
                                    key(entry.id) { SandboxEnvironmentGroup(entry, !state.busy, onAction) }
                                }
                                TextButton(onClick = { onAction(SandboxSettingsAction.AddEnvironment) }, enabled = !state.busy) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text(stringResource(R.string.sandbox_add_environment), modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxEnvironmentGroup(entry: SandboxEnvironmentDraft, enabled: Boolean, onAction: (SandboxSettingsAction) -> Unit) {
    val rotation by animateFloatAsState(if (entry.expanded) 180f else 0f, tween(300, easing = FastOutSlowInEasing), label = "Environment arrow")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = { onAction(SandboxSettingsAction.ToggleEnvironment(entry.id)) }, enabled = enabled, color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                // 折叠摘要只展示名称；值可能是令牌，不应在浏览列表时直接暴露。
                Text(entry.name.ifBlank { stringResource(R.string.sandbox_new_environment) }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ExpandMore, stringResource(if (entry.expanded) R.string.sandbox_collapse_environment else R.string.sandbox_expand_environment), Modifier.rotate(rotation))
            }
        }
        SandboxExpansion(entry.expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    modifier = Modifier.fillMaxWidth(),
                )
                IconButton(onClick = { onAction(SandboxSettingsAction.RemoveEnvironment(entry.id)) }, enabled = enabled, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.sandbox_remove_environment), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
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
    if (state.enabled) {
        PreferenceSaveBar(
            label = stringResource(if (state.busy) R.string.sandbox_saving else R.string.sandbox_save),
            onSave = { onAction(SandboxSettingsAction.Save) },
            enabled = state.canSave,
            notice = if (state.dirty) stringResource(R.string.sandbox_unsaved_changes) else null,
        )
    }
}

@Preview(name = "Configured light", widthDp = 390, heightDp = 780)
@Preview(name = "Configured dark", widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxConfiguredPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configured = true, apiKey = "preview-key"))

@Preview(name = "Runtime light", widthDp = 390, heightDp = 1080)
@Preview(name = "Runtime dark", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Runtime tablet", widthDp = 840, heightDp = 1080)
@Composable
private fun SandboxRuntimePreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configured = true, apiKey = "preview-key", configurationExpanded = true,
    environment = listOf(SandboxEnvironmentDraft(0, "LANG", "zh_CN.UTF-8"), SandboxEnvironmentDraft(1, expanded = true)), dirty = true))

@Preview(name = "Disabled", widthDp = 390, heightDp = 780)
@Composable
private fun SandboxDisabledPreview() = SandboxScreenPreview(SandboxSettingsUiState())

@Preview(name = "Disabled expanded light", widthDp = 390, heightDp = 1080)
@Preview(name = "Disabled expanded dark", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxDisabledExpandedPreview() = SandboxScreenPreview(SandboxSettingsUiState(configurationExpanded = true))

@Preview(name = "Pending", widthDp = 390, heightDp = 780)
@Composable
private fun SandboxPendingPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true))

@Preview(name = "Invalid fields", widthDp = 320, heightDp = 780, fontScale = 1.3f)
@Composable
private fun SandboxInvalidPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configurationExpanded = true, apiUrl = "192.168.1.10:3000", errors = setOf(SandboxSettingField.API_URL, SandboxSettingField.API_KEY)))

@Preview(name = "Local HTTP light", widthDp = 390, heightDp = 1080)
@Preview(name = "Local HTTP dark", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxLocalHttpPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configurationExpanded = true,
    apiUrl = "http://192.168.1.10:3000", sandboxUrl = "http://192.168.1.10:3002", apiKey = "preview-key", template = "base"))

@Composable
private fun SandboxScreenPreview(state: SandboxSettingsUiState) {
    AsssistantaiTheme {
        PreferenceScaffold(title = stringResource(R.string.sandbox_screen_title), onBack = {}, style = PreferenceStyle.Compact, bottomBar = { SandboxSettingsSaveBar(state, {}) }) {
            SandboxSettingsScreen(state, {}, {}, it)
        }
    }
}
