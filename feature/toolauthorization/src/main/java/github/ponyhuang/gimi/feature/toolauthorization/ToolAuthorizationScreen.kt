package github.ponyhuang.gimi.feature.toolauthorization

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun ToolAuthorizationRoute(
    onBack: () -> Unit,
    onNavigateToConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolAuthorizationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ToolAuthorizationEffects(viewModel.effects)
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.toolauth_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        ToolAuthorizationScreen(
            state = state,
            onAction = viewModel::onAction,
            onNavigateToConfiguration = onNavigateToConfiguration,
            modifier = scaffoldModifier,
        )
    }
}

@Composable
fun ToolAuthorizationScreen(
    state: ToolAuthorizationUiState,
    onAction: (ToolAuthorizationAction) -> Unit,
    onNavigateToConfiguration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Build,
                        title = stringResource(R.string.toolauth_tool_access_label),
                        subtitle = toolAccessModeDescription(state.toolAccessMode),
                        showDivider = true,
                        trailingContent = {
                            GimiSwitch(
                                checked = state.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE,
                                onCheckedChange = { on ->
                                    onAction(
                                        ToolAuthorizationAction.SetToolAccessMode(
                                            if (on) {
                                                ToolAccessMode.ALWAYS_AVAILABLE
                                            } else {
                                                ToolAccessMode.ON_DEMAND
                                            },
                                        ),
                                    )
                                },
                                enabled = !state.isMutationBlocked,
                            )
                        },
                    )
                    val configurationEnabled = state.isCustomizationEnabled && !state.isMutationBlocked
                    PreferenceListItem(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.toolauth_customize_label),
                        subtitle = stringResource(R.string.toolauth_customize_description),
                        showDivider = configurationEnabled,
                        onClick = {
                            if (!state.isMutationBlocked) {
                                onAction(ToolAuthorizationAction.SetCustomizationEnabled(!state.isCustomizationEnabled))
                            }
                        },
                        trailingContent = {
                            GimiSwitch(
                                checked = state.isCustomizationEnabled,
                                onCheckedChange = { enabled ->
                                    onAction(ToolAuthorizationAction.SetCustomizationEnabled(enabled))
                                },
                                enabled = !state.isMutationBlocked,
                            )
                        },
                    )
                    // 依赖配置按需展开：自定义关闭（或 Agent 占用）时整行隐藏，替代原先的置灰常显。
                    AnimatedVisibility(
                        visible = configurationEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        PreferenceListItem(
                            icon = Icons.Default.Checklist,
                            title = stringResource(R.string.toolauth_configure_tools),
                            subtitle = stringResource(R.string.toolauth_configure_subtitle),
                            onClick = onNavigateToConfiguration,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            },
                        )
                    }
                }
            }
            if (state.isMutationBlocked) {
                item {
                    PreferenceBanner(
                        text = stringResource(R.string.toolauth_agent_mutation_blocked),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ToolAuthorizationEffects(effects: SharedFlow<ToolAuthorizationEffect>) {
    val context = LocalContext.current
    val agentBusyMessage = stringResource(R.string.toolauth_agent_mutation_blocked)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is ToolAuthorizationEffect.ShowMessage -> {
                    val text = when (effect.message) {
                        ToolAuthorizationMessage.AgentBusy -> agentBusyMessage
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun toolAccessModeDescription(mode: ToolAccessMode): String = stringResource(
    when (mode) {
        ToolAccessMode.ON_DEMAND -> R.string.toolauth_tool_access_on_demand_description
        ToolAccessMode.ALWAYS_AVAILABLE -> R.string.toolauth_tool_access_always_description
    },
)

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ToolAuthorizationScreenCustomizationEnabledPreview() {
    AsssistantaiTheme {
        ToolAuthorizationScreen(
            state = ToolAuthorizationUiState(
                isCustomizationEnabled = true,
                tools = listOf(
                    ToolDescriptor(id = "web_search", name = "web_search", description = "搜索互联网。", isEnabled = true),
                ),
            ),
            onAction = {},
            onNavigateToConfiguration = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ToolAuthorizationScreenToolAccessAlwaysPreview() {
    AsssistantaiTheme {
        ToolAuthorizationScreen(
            state = ToolAuthorizationUiState(toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE),
            onAction = {},
            onNavigateToConfiguration = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ToolAuthorizationScreenMutationBlockedPreview() {
    AsssistantaiTheme {
        ToolAuthorizationScreen(
            state = ToolAuthorizationUiState(
                isCustomizationEnabled = false,
                isMutationBlocked = true,
            ),
            onAction = {},
            onNavigateToConfiguration = {},
        )
    }
}
