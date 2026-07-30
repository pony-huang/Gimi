package github.ponyhuang.gimi.feature.appfunctions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStatusHero

/** AppFunctions 尝鲜版总开关与提供应用列表。 */
@Composable
fun AppFunctionsSettingsScreen(
    state: AppFunctionsUiState,
    onAction: (AppFunctionsAction) -> Unit,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog = state.catalog
    val featureEnabled = catalog.selection.featureEnabled
    val switchEnabled = catalog.support == AppFunctionsSupport.AVAILABLE &&
        !state.isMutationBlocked
    val supportNotice = supportNotice(state)
    PreferencePageContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceStatusHero(
                    icon = Icons.Default.Science,
                    title = stringResource(R.string.appfunctions_experimental_title),
                    statusText = stringResource(
                        if (featureEnabled) {
                            R.string.appfunctions_status_enabled
                        } else {
                            R.string.appfunctions_status_disabled
                        },
                    ),
                    subtitle = stringResource(R.string.appfunctions_experimental_summary),
                    active = featureEnabled,
                    trailingContent = {
                        Switch(
                            checked = featureEnabled,
                            enabled = switchEnabled,
                            modifier = Modifier.testTag("appfunctions-master-switch"),
                            onCheckedChange = {
                                onAction(AppFunctionsAction.SetFeatureEnabled(it))
                            },
                        )
                    },
                )
            }
            supportNotice?.let { notice ->
                item {
                    AppFunctionsNotice(title = notice.first, summary = notice.second)
                }
            }
            if (state.isMutationBlocked) {
                item {
                    Text(
                        text = stringResource(R.string.appfunctions_agent_busy),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            if (featureEnabled) {
                item {
                    PreferenceSectionTitle(stringResource(R.string.appfunctions_apps_section))
                }
                if (catalog.isDiscovering) {
                    item { EmptyMessage(stringResource(R.string.appfunctions_discovering)) }
                } else if (catalog.functions.isEmpty()) {
                    item { EmptyMessage(stringResource(R.string.appfunctions_empty)) }
                }
                catalog.toAppItems().forEach { app ->
                    item(key = app.packageName) {
                        PreferenceListItem(
                            icon = Icons.Default.Apps,
                            title = app.label,
                            subtitle = stringResource(
                                R.string.appfunctions_app_count,
                                app.enabledCount,
                                app.totalCount,
                                app.unsupportedCount,
                            ),
                            onClick = { onOpenApp(app.packageName) },
                            trailingContent = {
                                Switch(
                                    checked = app.allEnabled,
                                    enabled = !state.isMutationBlocked && app.totalCount > 0,
                                    onCheckedChange = {
                                        onAction(
                                            AppFunctionsAction.SetAppEnabled(
                                                app.packageName,
                                                it,
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 单个应用的函数搜索、筛选和逐项授权页面。 */
@Composable
fun AppFunctionAppDetailScreen(
    packageName: String,
    state: AppFunctionsUiState,
    onAction: (AppFunctionsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val functions = state.catalog.functions
        .filter { function -> function.key.packageName == packageName }
        .filter { function ->
            state.query.isBlank() ||
                function.key.functionId.contains(state.query, ignoreCase = true) ||
                function.description.contains(state.query, ignoreCase = true)
        }
        .filter { function -> function.matches(state) }
    PreferencePageContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onAction(AppFunctionsAction.SetQuery(it)) },
                    label = { Text(stringResource(R.string.appfunctions_search_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    AppFunctionStatusFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onAction(AppFunctionsAction.SetFilter(filter)) },
                            label = { Text(filter.label()) },
                        )
                    }
                }
            }
            if (functions.isEmpty()) {
                item { EmptyMessage(stringResource(R.string.appfunctions_filter_empty)) }
            }
            functions.forEach { function ->
                item(key = function.key.encoded) {
                    val loadable = function.supported && function.providerEnabled
                    val appEnabled =
                        packageName in state.catalog.selection.enabledPackageNames
                    val selected = state.catalog.selection.isEnabled(function.key)
                    PreferenceListItem(
                        icon = Icons.Default.Functions,
                        title = function.key.functionId,
                        subtitle = function.subtitle(),
                        trailingContent = {
                            Switch(
                                checked = selected && loadable,
                                enabled = appEnabled && loadable && !state.isMutationBlocked,
                                onCheckedChange = {
                                    onAction(
                                        AppFunctionsAction.SetFunctionEnabled(function.key, it),
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun supportNotice(state: AppFunctionsUiState): Pair<String, String>? = when {
    state.catalog.errorMessage != null ->
        stringResource(R.string.appfunctions_discovery_error) to
            checkNotNull(state.catalog.errorMessage)
    state.catalog.support == AppFunctionsSupport.UNSUPPORTED_DEVICE ->
        stringResource(R.string.appfunctions_unsupported_title) to
            stringResource(R.string.appfunctions_unsupported_summary)
    state.catalog.support == AppFunctionsSupport.MISSING_SYSTEM_PERMISSION ->
        stringResource(R.string.appfunctions_permission_title) to
            stringResource(R.string.appfunctions_permission_summary)
    else -> null
}

@Composable
private fun AppFunctionStatusFilter.label(): String = stringResource(
    when (this) {
        AppFunctionStatusFilter.ALL -> R.string.appfunctions_filter_all
        AppFunctionStatusFilter.ENABLED -> R.string.appfunctions_filter_enabled
        AppFunctionStatusFilter.DISABLED -> R.string.appfunctions_filter_disabled
        AppFunctionStatusFilter.UNAVAILABLE -> R.string.appfunctions_filter_unavailable
    },
)

private fun AppFunctionDescriptor.matches(state: AppFunctionsUiState): Boolean {
    val enabled = state.catalog.selection.isEnabled(key) && supported && providerEnabled
    return when (state.filter) {
        AppFunctionStatusFilter.ALL -> true
        AppFunctionStatusFilter.ENABLED -> enabled
        AppFunctionStatusFilter.DISABLED -> supported && providerEnabled && !enabled
        AppFunctionStatusFilter.UNAVAILABLE -> !supported || !providerEnabled
    }
}

@Composable
private fun AppFunctionDescriptor.subtitle(): String = when {
    !supported -> stringResource(
        R.string.appfunctions_function_unsupported,
        unsupportedReason.orEmpty(),
    )
    !providerEnabled -> stringResource(R.string.appfunctions_provider_disabled)
    else -> description
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

@Composable
private fun AppFunctionsNotice(
    title: String,
    summary: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(30.dp),
        )
        Column(modifier = Modifier.padding(start = 28.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
