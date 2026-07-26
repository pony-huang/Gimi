package github.ponyhuang.asssistantai.feature.toolauthorization

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.asssistantai.ui.settings.SettingsBanner
import github.ponyhuang.asssistantai.ui.settings.SettingsBannerTone
import github.ponyhuang.asssistantai.ui.settings.SettingsListItem
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer

@Composable
fun ToolAuthorizationRoute(
    onNavigateToConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolAuthorizationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ToolAuthorizationScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateToConfiguration = onNavigateToConfiguration,
        modifier = modifier,
    )
}

@Composable
fun ToolAuthorizationScreen(
    state: ToolAuthorizationUiState,
    onAction: (ToolAuthorizationAction) -> Unit,
    onNavigateToConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SettingsListItem(
                    icon = Icons.Default.Build,
                    title = stringResource(R.string.toolauth_customize_label),
                    subtitle = stringResource(R.string.toolauth_customize_description),
                    onClick = {
                        if (!state.isMutationBlocked) {
                            onAction(ToolAuthorizationAction.SetCustomizationEnabled(!state.isCustomizationEnabled))
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = state.isCustomizationEnabled,
                            onCheckedChange = { enabled ->
                                onAction(ToolAuthorizationAction.SetCustomizationEnabled(enabled))
                            },
                            enabled = !state.isMutationBlocked,
                        )
                    },
                )
            }
            val configurationEnabled = state.isCustomizationEnabled && !state.isMutationBlocked
            item {
                SettingsListItem(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.toolauth_configure_tools),
                    subtitle = stringResource(
                        if (configurationEnabled) {
                            R.string.toolauth_configure_subtitle
                        } else {
                            R.string.toolauth_configure_disabled_subtitle
                        }
                    ),
                    onClick = if (configurationEnabled) onNavigateToConfiguration else null,
                    modifier = Modifier.alpha(if (configurationEnabled) 1f else 0.38f),
                    trailingContent = if (configurationEnabled) {
                        {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.CenterVertically),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            if (state.isMutationBlocked || !state.notice.isNullOrBlank()) {
                item {
                    SettingsBanner(
                        text = state.notice?.takeUnless { it.isBlank() }
                            ?: stringResource(R.string.toolauth_agent_mutation_blocked),
                        tone = if (state.isMutationBlocked) {
                            SettingsBannerTone.Error
                        } else {
                            SettingsBannerTone.Info
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
