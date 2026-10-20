package github.ponyhuang.gimi.feature.plugin

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceSectionNote
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun PluginSubscriptionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.plugin_subscription_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        PluginSubscriptionScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier.then(modifier),
        )
    }
}

/**
 * 插件订阅源编辑页：按顺序展示已追加的多个 HTTPS 订阅源，支持追加、移除与恢复默认。
 */
@Composable
fun PluginSubscriptionScreen(
    state: PluginSubscriptionUiState,
    onAction: (PluginSubscriptionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item {
                PreferenceSectionTitle(stringResource(R.string.plugin_subscription_section_sources))
                PreferenceGroupCard {
                    if (state.urls.isEmpty()) {
                        PreferenceListItem(
                            icon = Icons.Outlined.RssFeed,
                            title = stringResource(R.string.plugin_subscription_empty),
                            subtitle = stringResource(R.string.plugin_subscription_empty_subtitle),
                            showDivider = true,
                        )
                    }
                    state.urls.forEach { url ->
                        SubscriptionSourceRow(
                            url = url,
                            isDefault = url == state.defaultUrl,
                            onRemove = { onAction(PluginSubscriptionAction.Remove(url)) },
                        )
                    }
                    PreferenceListItem(
                        icon = Icons.Default.Add,
                        title = stringResource(R.string.plugin_subscription_add),
                        subtitle = stringResource(R.string.plugin_subscription_add_subtitle),
                        onClick = { onAction(PluginSubscriptionAction.AddClicked) },
                    )
                }
            }
            item {
                PreferenceSectionNote(stringResource(R.string.plugin_subscription_note))
            }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Outlined.SettingsBackupRestore,
                        title = stringResource(R.string.plugin_subscription_reset),
                        subtitle = stringResource(R.string.plugin_subscription_reset_subtitle),
                        onClick = { onAction(PluginSubscriptionAction.Reset) },
                    )
                }
            }
        }
    }

    if (state.isAddDialogVisible) {
        AddSubscriptionDialog(
            draftUrl = state.draftUrl,
            error = state.error,
            onDraftChange = { onAction(PluginSubscriptionAction.DraftChanged(it)) },
            onConfirm = { onAction(PluginSubscriptionAction.ConfirmAdd) },
            onDismiss = { onAction(PluginSubscriptionAction.DismissAdd) },
        )
    }
}

@Composable
private fun SubscriptionSourceRow(
    url: String,
    isDefault: Boolean,
    onRemove: () -> Unit,
) {
    PreferenceListItem(
        icon = Icons.Outlined.RssFeed,
        title = if (isDefault) {
            stringResource(R.string.plugin_subscription_official)
        } else {
            url.substringAfter("://").substringBefore('/').ifBlank { url }
        },
        subtitle = url,
        showDivider = true,
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.plugin_subscription_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun AddSubscriptionDialog(
    draftUrl: String,
    error: PluginSubscriptionError?,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorMessageRes = error?.messageRes()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_subscription_add_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferenceTextField(
                    value = draftUrl,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.plugin_subscription_url_label)) },
                    isError = errorMessageRes != null,
                    supportingText = if (errorMessageRes != null) {
                        { Text(stringResource(errorMessageRes)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                )
                Text(
                    text = stringResource(R.string.plugin_subscription_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.plugin_subscription_add_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.plugin_subscription_cancel))
            }
        },
    )
}

private fun PluginSubscriptionError.messageRes(): Int = when (this) {
    PluginSubscriptionError.INVALID_URL -> R.string.plugin_subscription_invalid_url
    PluginSubscriptionError.DUPLICATE -> R.string.plugin_subscription_duplicate
}

@Preview(name = "订阅源 · 浅色", showBackground = true, widthDp = 390, heightDp = 700, locale = "zh")
@Preview(name = "订阅源 · 深色", showBackground = true, widthDp = 390, heightDp = 700, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginSubscriptionScreenPreview() {
    AsssistantaiTheme {
        PluginSubscriptionScreen(
            state = PluginSubscriptionUiState(
                urls = listOf(
                    "https://github.com/pony-huang/Gimi/releases/latest/download/plugin-subscription.json",
                    "https://example.com/team-plugins.json",
                ),
                defaultUrl = "https://github.com/pony-huang/Gimi/releases/latest/download/plugin-subscription.json",
            ),
            onAction = {},
        )
    }
}

@Preview(name = "订阅源 · 空列表", showBackground = true, widthDp = 390, heightDp = 700, locale = "zh")
@Composable
private fun PluginSubscriptionScreenEmptyPreview() {
    AsssistantaiTheme {
        PluginSubscriptionScreen(
            state = PluginSubscriptionUiState(defaultUrl = "https://example.com/official.json"),
            onAction = {},
        )
    }
}
