package github.ponyhuang.gimi.feature.sandbox

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingField
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplate
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 仅管理下拉展开这一展示状态，查询和配置变更通过同步事件交给 ViewModel。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SandboxTemplatePicker(state: SandboxSettingsUiState, onAction: (SandboxSettingsAction) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expanded = menuExpanded && !state.busy
    val selected = state.templates.firstOrNull { it.templateId == state.template || state.template in it.names }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { open ->
            menuExpanded = open
            if (open) onAction(SandboxSettingsAction.LoadTemplates)
        }) {
            PreferenceTextField(
                value = state.template,
                onValueChange = { onAction(SandboxSettingsAction.SetTemplate(it)) },
                label = { Text(stringResource(R.string.sandbox_template)) },
                supportingText = {
                    Text(stringResource(if (SandboxSettingField.TEMPLATE in state.errors) R.string.sandbox_template_required else R.string.sandbox_template_help))
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded,
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable, enabled = !state.busy))
                },
                singleLine = true,
                enabled = !state.busy,
                isError = SandboxSettingField.TEMPLATE in state.errors,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = !state.busy),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { menuExpanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.sandbox_template_default)) }, onClick = {
                    onAction(SandboxSettingsAction.SetTemplate("code-interpreter-v1")); menuExpanded = false
                })
                state.templates.forEach { template ->
                    DropdownMenuItem(text = {
                        Column {
                            Text(template.names.firstOrNull() ?: template.templateId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.sandbox_template_details, template.templateId, stringResource(templateStatusText(template.buildStatus))),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }, onClick = {
                        onAction(SandboxSettingsAction.SetTemplate(template.templateId)); menuExpanded = false
                    })
                }
            }
        }
        selected?.names?.firstOrNull()?.let { name ->
            Text(stringResource(R.string.sandbox_template_selected, name), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                when (state.templateLoadState) {
                    SandboxTemplateLoadState.IDLE -> stringResource(R.string.sandbox_templates_hint)
                    SandboxTemplateLoadState.LOADING -> stringResource(R.string.sandbox_templates_loading)
                    SandboxTemplateLoadState.LOADED -> if (state.templates.isEmpty()) stringResource(R.string.sandbox_templates_empty)
                        else stringResource(R.string.sandbox_templates_count, state.templates.size)
                    SandboxTemplateLoadState.CREDENTIALS_REQUIRED -> stringResource(R.string.sandbox_templates_credentials)
                    SandboxTemplateLoadState.AUTHENTICATION -> stringResource(R.string.sandbox_templates_authentication)
                    SandboxTemplateLoadState.RATE_LIMIT -> stringResource(R.string.sandbox_templates_rate_limit)
                    SandboxTemplateLoadState.NETWORK -> stringResource(R.string.sandbox_templates_network)
                    SandboxTemplateLoadState.PROTOCOL, SandboxTemplateLoadState.UNAVAILABLE -> stringResource(R.string.sandbox_templates_failed)
                },
                modifier = Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onAction(SandboxSettingsAction.RefreshTemplates) },
                enabled = !state.busy && state.templateLoadState != SandboxTemplateLoadState.LOADING) {
                Text(stringResource(R.string.sandbox_templates_refresh))
            }
        }
    }
}

private fun templateStatusText(status: String?): Int = when (status) {
    "ready" -> R.string.sandbox_template_ready
    "building" -> R.string.sandbox_template_building
    "waiting" -> R.string.sandbox_template_waiting
    "error" -> R.string.sandbox_template_error
    else -> R.string.sandbox_template_unknown
}

@Preview(name = "Templates light", widthDp = 390)
@Preview(name = "Templates dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TemplatesPreview() = TemplatePickerPreview(SandboxTemplateLoadState.LOADED,
    listOf(SandboxTemplate("cli-template-id", listOf("my-project/cli-tools"), "ready")))

@Preview(name = "Templates loading", widthDp = 390)
@Preview(name = "Templates loading dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TemplatesLoadingPreview() = TemplatePickerPreview(SandboxTemplateLoadState.LOADING)

@Preview(name = "Templates empty", widthDp = 390)
@Preview(name = "Templates empty dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TemplatesEmptyPreview() = TemplatePickerPreview(SandboxTemplateLoadState.LOADED)

@Preview(name = "Templates failure", widthDp = 390)
@Preview(name = "Templates failure dark", widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TemplatesFailurePreview() = TemplatePickerPreview(SandboxTemplateLoadState.NETWORK)

@Composable
private fun TemplatePickerPreview(loadState: SandboxTemplateLoadState, templates: List<SandboxTemplate> = emptyList()) {
    AsssistantaiTheme {
        Surface {
            Column(Modifier.padding(16.dp)) {
                SandboxTemplatePicker(SandboxSettingsUiState(template = "cli-template-id", templates = templates, templateLoadState = loadState), {})
            }
        }
    }
}
