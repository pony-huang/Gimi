package github.ponyhuang.gimi.feature.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer

@Composable
fun PluginSettingsRoute(
    onNavigateToConfig: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PluginSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateToConfig = onNavigateToConfig,
        modifier = modifier,
    )
}

@Composable
fun PluginSettingsScreen(
    state: PluginSettingsUiState,
    onAction: (PluginSettingsAction) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        if (state.plugins.isEmpty()) {
            PreferenceBanner(
                text = stringResource(R.string.plugin_empty),
                tone = PreferenceBannerTone.Info,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(state.plugins, key = { it.id }) { plugin ->
                PreferenceListItem(
                    icon = Icons.Default.Extension,
                    title = plugin.id,
                    subtitle = stringResource(
                        R.string.plugin_item_subtitle,
                        plugin.version,
                        plugin.toolCount,
                    ),
                    onClick = { onNavigateToConfig(plugin.id) },
                    trailingContent = {
                        Switch(
                            checked = plugin.isEnabled,
                            onCheckedChange = { enabled ->
                                onAction(PluginSettingsAction.SetEnabled(plugin.id, enabled))
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun PluginConfigRoute(
    pluginId: String,
    modifier: Modifier = Modifier,
    viewModel: PluginConfigViewModel = hiltViewModel(),
) {
    LaunchedEffect(pluginId) { viewModel.load(pluginId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PluginConfigScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun PluginConfigScreen(
    state: PluginConfigUiState,
    onAction: (PluginConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.hasFields) {
                PreferenceBanner(
                    text = stringResource(R.string.plugin_config_no_fields),
                    tone = PreferenceBannerTone.Info,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(state.fields, key = { it.key }) { field ->
                    when (field.kind) {
                        PluginConfigFieldDescriptor.Kind.TEXT -> PluginTextField(
                            field = field,
                            onValueChange = { onAction(PluginConfigAction.SetValue(field.key, it)) },
                        )
                        PluginConfigFieldDescriptor.Kind.TOGGLE -> PluginToggleField(
                            field = field,
                            onCheckedChange = { onAction(PluginConfigAction.SetValue(field.key, it.toString())) },
                        )
                        PluginConfigFieldDescriptor.Kind.SELECT -> PluginSelectField(
                            field = field,
                            onSelect = { onAction(PluginConfigAction.SetValue(field.key, it)) },
                        )
                    }
                }
            }
            if (state.hasFields) {
                Button(
                    onClick = { onAction(PluginConfigAction.Save) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.plugin_config_save))
                }
            }
        }
    }
}

@Composable
private fun PluginTextField(
    field: PluginConfigFieldUiState,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = field.value,
        onValueChange = onValueChange,
        label = { Text(field.label) },
        singleLine = true,
        visualTransformation = if (field.secret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
    )
}

@Composable
private fun PluginToggleField(
    field: PluginConfigFieldUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    PreferenceListItem(
        icon = Icons.Default.Tune,
        title = field.label,
        trailingContent = {
            Switch(
                checked = field.value.toBoolean(),
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginSelectField(
    field: PluginConfigFieldUiState,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            field.options.forEach { option ->
                FilterChip(
                    selected = field.value == option,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}
