package github.ponyhuang.gimi.feature.toolauthorization

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
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
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
    PreferencePageContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                val configurationEnabled = state.isCustomizationEnabled && !state.isMutationBlocked
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Rule,
                        title = stringResource(R.string.toolauth_customize_label),
                        subtitle = stringResource(R.string.toolauth_customize_description),
                        showDivider = configurationEnabled,
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
                    // 依赖配置按需展开：自定义关闭（或 Agent 占用）时整行隐藏，替代原先的置灰常显。
                    AnimatedVisibility(
                        visible = configurationEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        PreferenceListItem(
                            icon = Icons.Default.Tune,
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
                        tone = PreferenceBannerTone.Error,
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
