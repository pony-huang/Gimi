package github.ponyhuang.gimi.feature.environment

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.domain.environment.EnvironmentStatus
import github.ponyhuang.gimi.domain.environment.RuntimeVersion
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.*
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 无状态屏幕的同步用户意图回调，由 Route 组装，屏幕不持有 ViewModel。 */
class EnvironmentActions(
    val enable: () -> Unit, val requestClose: () -> Unit, val close: () -> Unit, val dismiss: () -> Unit,
    val install: () -> Unit, val cancelInstall: () -> Unit, val setE2b: (Boolean) -> Unit,
    val add: () -> Unit, val edit: (Long, String, String) -> Unit, val remove: (Long) -> Unit,
    val toggle: (Long) -> Unit,
    val save: () -> Unit,
    val requestUpdate: () -> Unit, val update: () -> Unit, val dismissUpdate: () -> Unit,
)

@Composable
fun EnvironmentScreen(page: EnvironmentDestination, state: EnvironmentUiState, actions: EnvironmentActions,
    navigate: (NavKey) -> Unit, onOpenE2b: () -> Unit, onOpenWebsite: () -> Unit, onOpenFiles: () -> Unit,
    onRecover: () -> Unit, onBackgroundSettings: () -> Unit,
    modifier: Modifier = Modifier) {
    PreferencePageContainer(modifier.verticalScroll(rememberScrollState()), style = PreferenceStyle.Compact) {
        when (page) {
            EnvironmentDestination.Overview -> {
                PreferenceSectionTitle(stringResource(R.string.environment_title))
                PreferenceGroupCard {
                    PreferenceListItem(icon = Icons.Default.PhoneAndroid, title = stringResource(R.string.environment_local),
                        onClick = if (state.local.updateRequired || !state.local.connectionAvailable) ({ navigate(EnvironmentDestination.Local) }) else null,
                        subtitle = stringResource(when {
                            state.local.updateRequired -> R.string.environment_update_required
                            !state.local.connectionAvailable -> R.string.environment_connection_unavailable
                            state.local.updateAvailable -> R.string.environment_update_available
                            state.local.installed -> R.string.environment_ready
                            else -> R.string.environment_not_installed
                        }),
                        showDivider = true, trailingContent = {
                            GimiSwitch(state.local.enabled, { if (it) actions.enable() else actions.requestClose() }, enabled = state.local.installed && state.local.connectionAvailable && !state.busy && (!state.local.updateRequired || state.local.enabled))
                        })
                    PreferenceListItem(icon = Icons.Default.Memory, title = stringResource(R.string.environment_manage),
                        subtitle = stringResource(R.string.environment_manage_subtitle), onClick = { navigate(EnvironmentDestination.Local) },
                        showDivider = true)
                    EnvironmentLink(R.string.environment_directory, icon = Icons.Default.FolderOpen,
                        enabled = state.local.installed || state.local.updateRequired ||
                            (state.local.available && !state.local.connectionAvailable), onClick = {
                        if (!state.local.runtimeUsable) navigate(EnvironmentDestination.Local) else onOpenFiles()
                    })
                }
                Spacer(Modifier.height(12.dp))
                PreferenceGroupCard {
                    PreferenceSwitchRow(
                        icon = Icons.Default.CloudQueue,
                        title = stringResource(R.string.environment_e2b),
                        description = stringResource(if (state.e2bConfigured) R.string.environment_configured else R.string.environment_not_configured),
                        showDivider = true,
                        checked = state.e2bEnabled,
                        enabled = !state.busy,
                        onCheckedChange = actions.setE2b,
                    )
                    EnvironmentLink(R.string.environment_connection, icon = Icons.Default.Key, onClick = onOpenE2b)
                }
            }
            EnvironmentDestination.Local -> {
                PreferenceSectionTitle(stringResource(R.string.environment_runtime))
                RuntimeComponentCard(state.local, state.busy, actions.requestUpdate, onRecover, onBackgroundSettings)
                PreferenceSectionTitle(stringResource(R.string.environment_variables))
                PreferenceGroupCard {
                    PreferenceListItem(icon = Icons.Default.DataObject, title = stringResource(R.string.environment_variables),
                        subtitle = stringResource(R.string.environment_variables_summary, state.variables.size),
                        onClick = { navigate(EnvironmentDestination.Variables) })
                }
                PreferenceSectionTitle(stringResource(R.string.environment_base))
                PreferenceGroupCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_debian), null, Modifier.size(32.dp), tint = Color.Unspecified)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.environment_image_name), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            IconButton(onClick = onOpenWebsite) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.environment_website)) }
                        }
                        Text(stringResource(when {
                            state.local.available && !state.local.connectionAvailable -> R.string.environment_connection_unavailable
                            state.local.installing -> R.string.environment_installing
                            state.local.installed -> R.string.environment_ready
                            state.local.failed -> R.string.environment_install_failed
                            else -> R.string.environment_not_installed
                        }))
                        if (state.local.installing) {
                            LinearProgressIndicator(progress = { state.local.progress / 100f }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = actions.cancelInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.environment_cancel)) }
                        } else if (!state.local.installed && state.statusLoaded && state.local.connectionAvailable) {
                            Text(stringResource(R.string.environment_download_size), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = actions.install, enabled = !state.busy && !state.local.updateRequired, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (state.local.available) R.string.environment_install else R.string.environment_install_runtime))
                            }
                        }
                    }
                }
            }
            EnvironmentDestination.Variables -> {
                PreferenceSectionTitle(stringResource(R.string.environment_local))
                PreferenceSectionNote(stringResource(R.string.environment_variable_help))
                PreferenceGroupCard {
                    state.variables.forEachIndexed { index, row ->
                        key(row.id) { EnvironmentVariableGroup(row, showDivider = index < state.variables.lastIndex, enabled = !state.busy, actions) }
                    }
                }
                TextButton(onClick = actions.add, enabled = !state.busy,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.environment_add_variable), Modifier.padding(start = 6.dp))
                }
            }
        }
        if (state.error && state.local.connectionAvailable) Text(stringResource(R.string.environment_error), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(24.dp))
    }
    if (state.confirmClose) AlertDialog(onDismissRequest = actions.dismiss,
        title = { Text(stringResource(R.string.environment_close_title)) },
        text = { Text(stringResource(R.string.environment_close_body)) },
        confirmButton = { TextButton(onClick = actions.close) { Text(stringResource(R.string.environment_close)) } },
        dismissButton = { TextButton(onClick = actions.dismiss) { Text(stringResource(R.string.environment_cancel)) } })
    if (state.confirmUpdate) AlertDialog(onDismissRequest = actions.dismissUpdate,
        title = { Text(stringResource(R.string.environment_update_title)) },
        text = { Text(stringResource(R.string.environment_update_body)) },
        confirmButton = { TextButton(onClick = actions.update, enabled = !state.busy) { Text(stringResource(R.string.environment_stop_update)) } },
        dismissButton = { TextButton(onClick = actions.dismissUpdate, enabled = !state.busy) { Text(stringResource(R.string.environment_cancel)) } })
}

/** 环境变量页的固定保存区；通知文案由草稿状态推导，保存失败在此给出针对性原因。 */
@Composable
internal fun EnvironmentVariablesSaveBar(state: EnvironmentUiState, onSave: () -> Unit) {
    PreferenceSaveBar(
        label = stringResource(R.string.environment_save),
        onSave = onSave,
        enabled = !state.busy && state.variablesDirty,
        notice = when {
            state.saveError == EnvironmentSaveError.Validation -> stringResource(R.string.environment_save_invalid_variable)
            state.saveError == EnvironmentSaveError.Connection -> stringResource(R.string.environment_save_unavailable)
            state.variablesDirty -> stringResource(R.string.environment_unsaved_changes)
            state.saved -> stringResource(R.string.environment_saved)
            else -> null
        },
    )
}

/** 折叠摘要行用等宽字体展示变量名；编辑区收纳在展开态，删除与可见性切换随之内聚到组内。 */
@Composable
private fun EnvironmentVariableGroup(row: EnvironmentVariableDraft, showDivider: Boolean, enabled: Boolean, actions: EnvironmentActions) {
    val rotation by animateFloatAsState(
        if (row.expanded) 180f else 0f,
        tween(300, easing = FastOutSlowInEasing),
        label = "Variable arrow",
    )
    var valueVisible by rememberSaveable(row.id) { mutableStateOf(false) }
    Column {
        Surface(onClick = { actions.toggle(row.id) }, enabled = enabled, color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(row.name.ifBlank { stringResource(R.string.environment_variable_new) }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                Icon(Icons.Default.ExpandMore,
                    stringResource(if (row.expanded) R.string.environment_collapse_variable else R.string.environment_expand_variable),
                    Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VariableExpansion(row.expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PreferenceTextField(row.name, { actions.edit(row.id, it, row.value) },
                    label = { Text(stringResource(R.string.environment_variable_name)) },
                    singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
                PreferenceTextField(row.value, { actions.edit(row.id, row.name, it) },
                    label = { Text(stringResource(R.string.environment_variable_value)) },
                    singleLine = true, enabled = enabled,
                    visualTransformation = if (valueVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { valueVisible = !valueVisible }, enabled = enabled) {
                            Icon(if (valueVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                stringResource(if (valueVisible) R.string.environment_hide_value else R.string.environment_show_value))
                        }
                    },
                    modifier = Modifier.fillMaxWidth())
                IconButton(onClick = { actions.remove(row.id) }, enabled = enabled, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.environment_delete_variable),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun VariableExpansion(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(300, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(150)),
    ) { content() }
}

@Composable
private fun RuntimeComponentCard(status: EnvironmentStatus, busy: Boolean, onUpdate: () -> Unit,
    onRecover: () -> Unit = {}, onSettings: () -> Unit = {}) {
    val version = status.runtimeVersion
    PreferenceGroupCard {
        Column {
            PreferenceListItem(icon = Icons.Default.PhoneAndroid, title = stringResource(R.string.environment_runtime),
                subtitle = if (version != null) stringResource(R.string.environment_runtime_version,
                    version.name, version.code) else stringResource(R.string.environment_not_installed))
            if (status.available && (!status.connectionAvailable || status.backgroundRestricted == true)) {
                Text(stringResource(R.string.environment_background_help), Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!status.updateRequired) OutlinedButton(onClick = onRecover, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.environment_recover))
                }
            }
            if (status.available) TextButton(onClick = onSettings, modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.environment_background_settings))
            }
            if (status.updateRequired || status.updateAvailable) {
                Text(stringResource(if (status.updateRequired) R.string.environment_update_required else R.string.environment_update_available),
                    Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (status.updateAvailable) {
                    Button(onClick = onUpdate, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(stringResource(R.string.environment_update_to, status.bundledRuntimeVersion?.name.orEmpty()))
                    }
                } else {
                    Text(stringResource(R.string.environment_update_main_app), Modifier.padding(20.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuntimeDisconnectedPreview() {
    GimiTheme {
        RuntimeComponentCard(EnvironmentStatus(available = true, connectionAvailable = false,
            runtimeVersion = RuntimeVersion(66, "1.0.0", 2), bundledRuntimeVersion = RuntimeVersion(66, "1.0.0", 2)), false, {})
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuntimeOptionalUpdatePreview() {
    GimiTheme {
        RuntimeComponentCard(EnvironmentStatus(available = true,
            runtimeVersion = RuntimeVersion(65, "0.13.0", 1), bundledRuntimeVersion = RuntimeVersion(66, "0.14.0", 1)), false, {})
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuntimeRequiredUpdatePreview() {
    GimiTheme {
        RuntimeComponentCard(EnvironmentStatus(available = true,
            runtimeVersion = RuntimeVersion(65, "0.13.0", 0), bundledRuntimeVersion = RuntimeVersion(66, "0.14.0", 1)), false, {})
    }
}

// ---------------------------------------------------------------- 整页预览

@Preview(name = "总览", showBackground = true, widthDp = 390, heightDp = 780)
@Preview(name = "总览深色", showBackground = true, widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OverviewPagePreview() = EnvironmentScreenPreview(EnvironmentDestination.Overview, previewState())

@Preview(name = "本地环境", showBackground = true, widthDp = 390, heightDp = 880)
@Preview(name = "本地环境深色", showBackground = true, widthDp = 390, heightDp = 880, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LocalPagePreview() = EnvironmentScreenPreview(EnvironmentDestination.Local, previewState())

@Preview(name = "环境变量", showBackground = true, widthDp = 390, heightDp = 880)
@Preview(name = "环境变量深色", showBackground = true, widthDp = 390, heightDp = 880, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VariablesPagePreview() = EnvironmentScreenPreview(EnvironmentDestination.Variables,
    previewState(variables = listOf(
        EnvironmentVariableDraft(0, "PYTHONUNBUFFERED", "1"),
        EnvironmentVariableDraft(1, "API_TOKEN", "preview-value", expanded = true),
    ), variablesDirty = true))

private fun previewState(variables: List<EnvironmentVariableDraft> = emptyList(), variablesDirty: Boolean = false) = EnvironmentUiState(
    local = EnvironmentStatus(available = true, installed = true, enabled = true,
        runtimeVersion = RuntimeVersion(66, "1.0.0", 2), bundledRuntimeVersion = RuntimeVersion(66, "1.0.0", 2)),
    variables = variables, variablesDirty = variablesDirty, statusLoaded = true,
)

private fun previewActions() = EnvironmentActions({}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, {}, {}, {}, {})

@Composable
private fun EnvironmentScreenPreview(page: EnvironmentDestination, state: EnvironmentUiState) {
    GimiTheme {
        PreferenceScaffold(
            title = stringResource(when (page) {
                EnvironmentDestination.Overview -> R.string.environment_title
                EnvironmentDestination.Local -> R.string.environment_local
                EnvironmentDestination.Variables -> R.string.environment_variables
            }),
            onBack = {},
            style = PreferenceStyle.Compact,
            bottomBar = { if (page == EnvironmentDestination.Variables) EnvironmentVariablesSaveBar(state, {}) },
        ) { modifier ->
            EnvironmentScreen(page, state, previewActions(), {}, {}, {}, {}, {}, {}, modifier)
        }
    }
}

@Composable
private fun EnvironmentLink(label: Int, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    // 禁用态保留 onClick 交给 rowEnabled 处理，让列表项呈现统一的置灰外观。
    PreferenceListItem(icon = icon, title = stringResource(label), onClick = onClick, rowEnabled = enabled)
}
