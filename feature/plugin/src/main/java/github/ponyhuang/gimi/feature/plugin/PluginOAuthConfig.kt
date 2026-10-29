package github.ponyhuang.gimi.feature.plugin

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 公共 OAuth 配置装配：能力由动作声明决定，不识别服务商或插件 ID。 */
@Composable
internal fun PluginOAuthConfigRoute(
    config: PluginConfigUiState,
    onAction: (PluginConfigAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginOAuthConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val supported = config.actions.any { it.id == OAUTH_STATUS_ACTION }
    val busy = config.isAnyActionRunning || config.callback != null
    LaunchedEffect(config.pluginId, supported, busy) {
        viewModel.bind(config.pluginId, supported, busy)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!busy) viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.cancelCheck() }
    DisposableEffect(config.pluginId) { onDispose { viewModel.cancelCheck() } }
    val reauthorize = stringResource(R.string.plugin_oauth_reauthorize)
    PluginConfigScreen(
        state = config.copy(actions = config.actions.filter { it.id != OAUTH_STATUS_ACTION }.map {
            if (supported && it.id == OAUTH_LOGIN_ACTION && state.authorization == PluginOAuthAuthorization.Authorized) {
                it.copy(label = reauthorize)
            } else it
        }),
        onAction = { action ->
            if (action is PluginConfigAction.RunAction) viewModel.cancelCheck()
            onAction(action)
            if (action == PluginConfigAction.Save) {
                viewModel.cancelCheck()
                viewModel.refresh()
            }
        },
        onBack = onBack,
        modifier = modifier,
        // 登录表单由登录动作保存；辅助链接不需要额外的保存入口。
        showSaveAction = !supported || config.actions.any {
            it.id != OAUTH_STATUS_ACTION && it.id != OAUTH_LOGIN_ACTION &&
                !it.id.startsWith(CONFIG_LINK_ACTION_PREFIX)
        },
        headerContent = {
            if (supported && state.pluginId == config.pluginId) {
                PluginOAuthSection(state, viewModel::refresh)
            }
        },
    )
}

/** 无状态 OAuth 状态展示，可供不同服务商的配置页复用。 */
@Composable
internal fun PluginOAuthSection(state: PluginOAuthConfigUiState, onRetry: () -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.plugin_authorization_status, stringResource(when (state.authorization) {
                PluginOAuthAuthorization.Checking -> R.string.plugin_authorization_checking
                PluginOAuthAuthorization.Waiting -> R.string.plugin_authorization_waiting
                PluginOAuthAuthorization.Expired -> R.string.plugin_authorization_expired
                PluginOAuthAuthorization.Authorized -> R.string.plugin_authorized
                PluginOAuthAuthorization.Unauthorized -> R.string.plugin_unauthorized
                PluginOAuthAuthorization.Failed -> R.string.plugin_authorization_failed
            })),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.authorization == PluginOAuthAuthorization.Failed) {
            state.failureMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.plugin_authorization_retry))
            }
        }
        Text(
            stringResource(R.string.plugin_oauth_credentials_help),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginOAuthAuthorizedPreview() = PluginOAuthPreview(PluginOAuthAuthorization.Authorized)

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginOAuthUnauthorizedPreview() = PluginOAuthPreview(PluginOAuthAuthorization.Unauthorized)

@Preview(showBackground = true)
@Composable
private fun PluginOAuthCheckingPreview() = PluginOAuthPreview(PluginOAuthAuthorization.Checking)

@Preview(showBackground = true)
@Composable
private fun PluginOAuthFailedPreview() = PluginOAuthPreview(PluginOAuthAuthorization.Failed)

@Composable
private fun PluginOAuthPreview(authorization: PluginOAuthAuthorization) {
    AsssistantaiTheme {
        PluginConfigScreen(
            state = PluginConfigUiState(
                pluginId = "example-provider",
                actions = listOf(
                    PluginActionUiState("link:console", "开发者控制台 ↗"),
                    PluginActionUiState(OAUTH_LOGIN_ACTION, "授权登录"),
                ),
            ),
            onAction = {},
            headerContent = {
                PluginOAuthSection(PluginOAuthConfigUiState(enabled = true, authorization = authorization), {})
            },
        )
    }
}

@Preview(name = "等待授权", showBackground = true)
@Preview(name = "等待授权深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginOAuthWaitingPreview() = PluginOAuthPreview(PluginOAuthAuthorization.Waiting)

@Preview(name = "授权过期", showBackground = true)
@Preview(name = "授权过期深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginOAuthExpiredPreview() = PluginOAuthPreview(PluginOAuthAuthorization.Expired)
