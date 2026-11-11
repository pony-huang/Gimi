package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 语音服务的连接配置区。
 *
 * 与聊天服务的差异：语音服务没有"接口标准"可选，因此直接摊开显示；端点由厂商自有的
 * 服务（实时语音与官方语音端点）不展示地址输入，改以小字提示写死的官方地址，
 * 百炼服务另提供可选的业务空间 ID。
 *
 * 会话音色不在这里配置：它与具体 Realtime 模型绑定（不同代际音色集互不相同），
 * 因此由默认模型页按所选模型管理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechServiceConnectionSection(
    apiKey: String,
    isApiKeyVisible: Boolean,
    isTesting: Boolean,
    testResult: ServiceConnectionResult?,
    isEditingEnabled: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleApiKeyVisibility: () -> Unit,
    onTestConnection: () -> Unit,
    onOpenKeyHelpUrl: () -> Unit,
    /**
     * 是否展示"接入地址"输入框。
     *
     * 端点可配置的服务（OpenAI 兼容代理类）展示；端点由厂商自有的服务不展示，
     * 用户填写的地址不会参与请求构造。
     */
    showsEndpointField: Boolean = false,
    endpoint: String = "",
    onEndpointChange: (String) -> Unit = {},
    /** 写死的官方接入地址提示；非空时以小字展示（端点由厂商自有的服务）。 */
    endpointHint: String? = null,
    /** 百炼业务空间 ID 草稿；仅 [showsWorkspaceField] 为 true 的服务消费。 */
    workspaceId: String = "",
    /** 是否展示业务空间 ID 输入框（百炼服务专属）。 */
    showsWorkspaceField: Boolean = false,
    onWorkspaceIdChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PreferenceSectionTitle(stringResource(R.string.modelsettings_section_connection))
    PreferenceGroupCard(modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.modelsettings_api_key_label)) },
                singleLine = true,
                enabled = isEditingEnabled,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    IconButton(onClick = onToggleApiKeyVisibility) {
                        Icon(
                            if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            stringResource(
                                if (isApiKeyVisible) R.string.modelsettings_api_key_hide
                                else R.string.modelsettings_api_key_show,
                            ),
                        )
                    }
                },
            )
            TextButton(onClick = onOpenKeyHelpUrl) { Text(stringResource(R.string.modelsettings_api_key_get_key)) }
        }
        if (showsWorkspaceField) {
            // 业务空间 ID 可选:留空走官方默认域名(密钥所属的默认业务空间)。
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = workspaceId,
                    onValueChange = onWorkspaceIdChange,
                    singleLine = true,
                    enabled = isEditingEnabled,
                    label = { Text(stringResource(R.string.modelsettings_speech_workspace_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.modelsettings_speech_workspace_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (endpointHint != null) {
            // 端点由厂商自有:展示写死的官方地址,让用户知道请求发往哪里、无法也不会需要自定义。
            Text(
                stringResource(R.string.modelsettings_speech_endpoint_fixed_hint, endpointHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (showsEndpointField) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                label = { Text(stringResource(R.string.modelsettings_speech_endpoint_label)) },
                singleLine = true,
                enabled = isEditingEnabled,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        OutlinedButton(
            onClick = onTestConnection,
            enabled = isEditingEnabled && !isTesting && apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(stringResource(if (isTesting) R.string.modelsettings_key_testing else R.string.modelsettings_api_key_test))
        }
        when (testResult) {
            ServiceConnectionResult.Succeeded -> Text(
                stringResource(R.string.modelsettings_notice_connection_succeeded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            ServiceConnectionResult.AuthFailed -> Text(
                stringResource(R.string.modelsettings_notice_connection_failed_auth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            ServiceConnectionResult.NetworkFailed -> Text(
                stringResource(R.string.modelsettings_notice_connection_failed_network),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            ServiceConnectionResult.Unsupported -> Text(
                stringResource(R.string.modelsettings_notice_connection_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            null -> Unit
        }
    }
}

/** 连接区的非敏感固定预览入参;预览需要真实回调签名但不需要行为。 */
private fun previewOnChange(): (String) -> Unit = {}

@Preview(name = "Qwen 业务空间", showBackground = true, widthDp = 390)
@Preview(name = "Qwen 业务空间 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QwenWorkspaceSectionPreview() {
    GimiTheme {
        SpeechServiceConnectionSection(
            apiKey = "sk-preview",
            isApiKeyVisible = false,
            isTesting = false,
            testResult = null,
            isEditingEnabled = true,
            onApiKeyChange = { previewOnChange() },
            onToggleApiKeyVisibility = {},
            onTestConnection = {},
            onOpenKeyHelpUrl = {},
            endpointHint = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime",
            workspaceId = "",
            showsWorkspaceField = true,
            onWorkspaceIdChange = { previewOnChange() },
        )
    }
}

@Preview(name = "可配置接入地址", showBackground = true, widthDp = 390)
@Preview(name = "可配置接入地址 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfiguredEndpointPreview() {
    GimiTheme {
        SpeechServiceConnectionSection(
            apiKey = "sk-preview",
            isApiKeyVisible = false,
            isTesting = false,
            testResult = null,
            isEditingEnabled = true,
            onApiKeyChange = { previewOnChange() },
            onToggleApiKeyVisibility = {},
            onTestConnection = {},
            onOpenKeyHelpUrl = {},
            showsEndpointField = true,
            endpoint = "https://api.minimaxi.com/v1",
            onEndpointChange = { previewOnChange() },
        )
    }
}
