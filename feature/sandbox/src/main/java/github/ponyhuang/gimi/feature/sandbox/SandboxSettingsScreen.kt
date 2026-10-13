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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingField
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSwitchItem
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 无状态设置屏幕；字段保存、校验、展开和密钥展示均由输入状态和事件驱动。 */
@Composable
fun SandboxSettingsScreen(state: SandboxSettingsUiState, onAction: (SandboxSettingsAction) -> Unit, modifier: Modifier = Modifier) {
    val advancedArrowRotation by animateFloatAsState(
        targetValue = if (state.advancedExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "Advanced arrow rotation",
    )
    PreferencePageContainer(modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                PreferenceGroupCard {
                    PreferenceSwitchItem(
                        icon = Icons.Default.Code,
                        title = stringResource(R.string.sandbox_e2b),
                        subtitle = stringResource(when {
                            !state.enabled -> R.string.sandbox_disabled_description
                            !state.configured -> R.string.sandbox_pending_description
                            else -> R.string.sandbox_configured_description
                        }),
                        checked = state.enabled,
                        switchEnabled = !state.busy,
                        onCheckedChange = { onAction(SandboxSettingsAction.SetEnabled(it)) },
                        onClick = { onAction(SandboxSettingsAction.SetEnabled(!state.enabled)) },
                        showDivider = false,
                    )
                    AnimatedVisibility(
                        visible = state.enabled,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SandboxField(state.domain, { onAction(SandboxSettingsAction.SetDomain(it)) },
                                    stringResource(R.string.sandbox_domain), stringResource(R.string.sandbox_domain_help),
                                    SandboxSettingField.DOMAIN in state.errors, !state.busy)
                                OutlinedTextField(
                                    value = state.apiKey,
                                    onValueChange = { onAction(SandboxSettingsAction.SetApiKey(it)) },
                                    label = { Text(stringResource(R.string.sandbox_api_key)) },
                                    supportingText = { Text(stringResource(if (SandboxSettingField.API_KEY in state.errors) R.string.sandbox_key_required else R.string.sandbox_key_help)) },
                                    visualTransformation = if (state.keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { onAction(SandboxSettingsAction.ToggleKeyVisibility) }, enabled = !state.busy) {
                                            Icon(if (state.keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = stringResource(if (state.keyVisible) R.string.sandbox_hide_key else R.string.sandbox_show_key))
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    isError = SandboxSettingField.API_KEY in state.errors,
                                    enabled = !state.busy,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            PreferenceListItem(icon = Icons.Default.Tune, title = stringResource(R.string.sandbox_advanced),
                                subtitle = stringResource(R.string.sandbox_advanced_description), onClick = { onAction(SandboxSettingsAction.ToggleAdvanced) },
                                trailingContent = {
                                    Icon(Icons.Default.ExpandMore, modifier = Modifier.rotate(advancedArrowRotation),
                                        contentDescription = stringResource(if (state.advancedExpanded) R.string.sandbox_collapse_advanced else R.string.sandbox_expand_advanced))
                                })
                            AnimatedVisibility(
                                visible = state.advancedExpanded,
                                enter = expandVertically(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    expandFrom = Alignment.Top,
                                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                                exit = shrinkVertically(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                    shrinkTowards = Alignment.Top,
                                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SandboxTemplatePicker(state, onAction)
                                    SandboxField(state.idleTimeoutSeconds, { onAction(SandboxSettingsAction.SetIdleTimeout(it)) },
                                        stringResource(R.string.sandbox_idle_timeout), stringResource(if (SandboxSettingField.IDLE_TIMEOUT in state.errors) R.string.sandbox_timeout_invalid else R.string.sandbox_timeout_help),
                                        SandboxSettingField.IDLE_TIMEOUT in state.errors, !state.busy, KeyboardOptions(keyboardType = KeyboardType.Number))
                                    Text(stringResource(R.string.sandbox_environment), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(R.string.sandbox_environment_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    state.environment.forEach { entry ->
                                        key(entry.id) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = MaterialTheme.shapes.large,
                                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    OutlinedTextField(value = entry.name, onValueChange = { onAction(SandboxSettingsAction.SetEnvironmentName(entry.id, it)) },
                                                        label = { Text(stringResource(R.string.sandbox_environment_name)) }, singleLine = true,
                                                        enabled = !state.busy, isError = entry.nameError,
                                                        supportingText = if (entry.nameError) { { Text(stringResource(R.string.sandbox_environment_invalid)) } } else null,
                                                        modifier = Modifier.fillMaxWidth())
                                                    OutlinedTextField(value = entry.value, onValueChange = { onAction(SandboxSettingsAction.SetEnvironmentValue(entry.id, it)) },
                                                        label = { Text(stringResource(R.string.sandbox_environment_value)) }, singleLine = true,
                                                        enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                                                    IconButton(
                                                        onClick = { onAction(SandboxSettingsAction.RemoveEnvironment(entry.id)) },
                                                        enabled = !state.busy,
                                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                                    ) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.sandbox_remove_environment))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    TextButton(onClick = { onAction(SandboxSettingsAction.AddEnvironment) }, enabled = !state.busy) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Text(stringResource(R.string.sandbox_add_environment))
                                    }
                                }
                            }
                            Button(onClick = { onAction(SandboxSettingsAction.Save) }, enabled = !state.busy,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth(0.5f)) {
                                Text(stringResource(if (state.busy) R.string.sandbox_saving else R.string.sandbox_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxField(value: String, onValueChange: (String) -> Unit, label: String, help: String, error: Boolean, enabled: Boolean, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, supportingText = { Text(help) },
        singleLine = true, isError = error, enabled = enabled, keyboardOptions = keyboardOptions, modifier = Modifier.fillMaxWidth())
}

@Preview(name = "Configured light", widthDp = 390, heightDp = 780)
@Preview(name = "Configured dark", widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxConfiguredPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configured = true, apiKey = "preview-key"))

@Preview(name = "Advanced light", widthDp = 390, heightDp = 1080)
@Preview(name = "Advanced dark", widthDp = 390, heightDp = 1080, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SandboxAdvancedPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, configured = true, apiKey = "preview-key", advancedExpanded = true,
    environment = listOf(SandboxEnvironmentDraft(0, "LANG", "zh_CN.UTF-8"))))

@Preview(name = "Disabled", widthDp = 390, heightDp = 780)
@Composable
private fun SandboxDisabledPreview() = SandboxScreenPreview(SandboxSettingsUiState())

@Preview(name = "Pending", widthDp = 390, heightDp = 780)
@Composable
private fun SandboxPendingPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true))

@Preview(name = "Invalid fields", widthDp = 390, heightDp = 780)
@Composable
private fun SandboxInvalidPreview() = SandboxScreenPreview(SandboxSettingsUiState(enabled = true, domain = "https://e2b.app", errors = setOf(SandboxSettingField.DOMAIN, SandboxSettingField.API_KEY)))

@Composable
private fun SandboxScreenPreview(state: SandboxSettingsUiState) {
    AsssistantaiTheme {
        PreferenceScaffold(title = stringResource(R.string.sandbox_screen_title), onBack = {}) {
            SandboxSettingsScreen(state, {}, it)
        }
    }
}
