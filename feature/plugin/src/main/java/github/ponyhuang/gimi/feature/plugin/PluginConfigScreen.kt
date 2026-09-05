package github.ponyhuang.gimi.feature.plugin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun PluginConfigRoute(
    pluginId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginConfigViewModel = hiltViewModel(),
) {
    LaunchedEffect(pluginId) { viewModel.load(pluginId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PluginConfigEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message ?: effect.messageRes?.let(context::getString).orEmpty(),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    PluginConfigScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PluginConfigScreen(
    state: PluginConfigUiState,
    onAction: (PluginConfigAction) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val callback = state.callback
    PreferenceScaffold(
        title = stringResource(
            if (callback == null) R.string.plugin_config_title else R.string.plugin_action_callback_title,
        ),
        onBack = if (callback == null) {
            onBack
        } else {
            { onAction(PluginConfigAction.DismissActionCallback) }
        },
    ) { contentModifier ->
        if (callback != null) {
            PluginActionCallbackScreen(
                callback = callback,
                onCallback = { values ->
                    onAction(PluginConfigAction.ReceiveActionCallback(callback.actionId, values))
                },
                modifier = contentModifier,
            )
            return@PreferenceScaffold
        }
        PluginConfigContent(
            state = state,
            onAction = onAction,
            modifier = contentModifier.then(modifier),
        )
    }
}

@Composable
private fun PluginConfigContent(
    state: PluginConfigUiState,
    onAction: (PluginConfigAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.hasFields && !state.hasActions) {
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
                item {
                    // 配置字段与动作整组放进一张卡片，行间靠组件自身间距分隔。
                    PreferenceGroupCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.fields.forEach { field ->
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
                            state.actions.forEach { action ->
                                Button(
                                    onClick = { onAction(PluginConfigAction.RunAction(action.id)) },
                                    enabled = !action.running,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        if (action.running) {
                                            stringResource(R.string.plugin_action_running)
                                        } else {
                                            action.label
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.hasFields) {
                Button(
                    onClick = { onAction(PluginConfigAction.Save) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
