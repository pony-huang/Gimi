package github.ponyhuang.gimi.feature.toolauthorization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun ToolAuthorizationConfigurationRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolAuthorizationConfigurationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ToolAuthorizationEffects(viewModel.effects)
    PreferenceScaffold(
        title = stringResource(R.string.toolauth_configuration_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        ToolAuthorizationConfigurationScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolAuthorizationConfigurationScreen(
    state: ToolAuthorizationConfigurationUiState,
    onAction: (ToolAuthorizationConfigurationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (state.isMutationBlocked) {
                PreferenceBanner(
                    text = stringResource(R.string.toolauth_agent_mutation_blocked),
                    tone = PreferenceBannerTone.Error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchAndFilterRow(state, onAction)
                        Text(
                            text = stringResource(
                                R.string.toolauth_count_label,
                                state.enabledCount,
                                state.tools.size,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (state.visibleTools.isEmpty()) {
                    item {
                        Text(
                            text = if (state.query.isBlank() && state.filter == ToolAuthorizationFilter.ALL) {
                                stringResource(R.string.toolauth_empty_state)
                            } else {
                                stringResource(R.string.toolauth_no_match)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.visibleTools, key = ToolDescriptor::id) { tool ->
                        ToolAuthorizationRow(
                            tool = tool,
                            enabled = !state.isMutationBlocked,
                            onEnabledChange = { enabled ->
                                onAction(ToolAuthorizationConfigurationAction.SetEnabled(tool.id, enabled))
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilterRow(
    state: ToolAuthorizationConfigurationUiState,
    onAction: (ToolAuthorizationConfigurationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    DockedSearchBar(
        query = state.query,
        onQueryChange = { onAction(ToolAuthorizationConfigurationAction.Search(it)) },
        onSearch = { onAction(ToolAuthorizationConfigurationAction.Search(it)) },
        active = false,
        onActiveChange = { },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.toolauth_search_label)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = stringResource(
                            R.string.toolauth_filter_content_description,
                            stringResource(state.filter.labelRes()),
                        ),
                        tint = if (state.filter == ToolAuthorizationFilter.ALL) {
                            LocalContentColor.current
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    ToolAuthorizationFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes())) },
                            onClick = {
                                onAction(ToolAuthorizationConfigurationAction.SetFilter(option))
                                expanded = false
                            },
                            trailingIcon = if (option == state.filter) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
    ) {
    }
}

private fun ToolAuthorizationFilter.labelRes(): Int = when (this) {
    ToolAuthorizationFilter.ALL -> R.string.toolauth_filter_all
    ToolAuthorizationFilter.ENABLED -> R.string.toolauth_filter_enabled
    ToolAuthorizationFilter.DISABLED -> R.string.toolauth_filter_disabled
}
