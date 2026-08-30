package github.ponyhuang.gimi.feature.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mcp.model.McpServer
import github.ponyhuang.gimi.domain.mcp.model.McpTransport
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceNavigationCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun McpServerListScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onCreateServer: () -> Unit,
    onImportServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isMutationBlocked) {
                PreferenceBanner(
                    text = stringResource(R.string.mcp_agent_mutation_blocked),
                    tone = PreferenceBannerTone.Error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.servers.isEmpty()) {
                McpEmptyState(
                    onCreateServer = onCreateServer,
                    onImportServers = onImportServers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    // 每台服务器一张可展开卡片，展开能力详情时保持 One UI 的分组外观。
                    items(state.servers, key = McpServer::id) { server ->
                        PreferenceGroupCard(modifier = Modifier.padding(bottom = 8.dp)) {
                            McpServerCard(
                                server = server,
                                mutationEnabled = !state.isMutationBlocked,
                                expanded = state.expandedServerId == server.id,
                                capabilityState = state.capabilities[server.id],
                                onClick = { onAction(McpSettingsAction.ServerCardClicked(server.id)) },
                                onEditClick = { onNavigateToEditor(server.id) },
                                onToggleEnabled = {
                                    onAction(McpSettingsAction.ToggleServer(server, it))
                                },
                                onRetryCapabilities = {
                                    onAction(McpSettingsAction.RefreshCapabilities(server.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 空态是首次用户的入口：直接给出两条路径（新建 / 导入），
 * 让首次配置跳过「添加方式」中转页。
 */
@Composable
private fun McpEmptyState(
    onCreateServer: () -> Unit,
    onImportServers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 空态主视觉用 MCP 品牌图标，与设置页入口保持同一标识。
        Icon(
            imageVector = ImageVector.vectorResource(github.ponyhuang.gimi.core.designsystem.R.drawable.ic_mcp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp),
        )
        Text(
            stringResource(R.string.mcp_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            stringResource(R.string.mcp_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onCreateServer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
        ) {
            Text(stringResource(R.string.mcp_empty_action_new))
        }
        OutlinedButton(
            onClick = onImportServers,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Text(stringResource(R.string.mcp_empty_action_import))
        }
    }
}

@Composable
fun McpServerAddOptionsScreen(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
        ) {
            PreferenceSectionTitle(text = stringResource(R.string.mcp_section_add_methods))
            PreferenceGroupCard {
                PreferenceNavigationCard(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.mcp_method_new_title),
                    subtitle = stringResource(R.string.mcp_method_new_subtitle),
                    onClick = onCreate,
                    showDivider = true,
                )
                PreferenceNavigationCard(
                    icon = Icons.Default.ContentPaste,
                    title = stringResource(R.string.mcp_method_import_title),
                    subtitle = stringResource(R.string.mcp_method_import_subtitle),
                    onClick = onImport,
                )
            }
        }
    }
}

@Composable
fun McpServerImportScreen(
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    PreferencePageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreferenceSectionTitle(text = stringResource(R.string.mcp_section_import_mcp))
            PreferenceGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.mcp_import_help))
                    OutlinedTextField(
                        value = state.importJson,
                        onValueChange = { onAction(McpSettingsAction.ImportJsonChanged(it)) },
                        label = { Text(stringResource(R.string.mcp_field_json_label)) },
                        placeholder = { Text(stringResource(R.string.mcp_field_json_placeholder)) },
                        minLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.importResult?.let { result ->
                        val resultText = result.error ?: if (result.skipped > 0) {
                            stringResource(
                                R.string.mcp_import_result_with_skipped,
                                result.created,
                                result.updated,
                                result.skipped,
                            )
                        } else {
                            stringResource(
                                R.string.mcp_import_result,
                                result.created,
                                result.updated,
                            )
                        }
                        Text(resultText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.getText()?.text?.let {
                                    onAction(McpSettingsAction.ImportJsonChanged(it))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.mcp_paste),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Button(
                            onClick = { onAction(McpSettingsAction.ImportServers) },
                            enabled = state.importJson.isNotBlank() && !state.isMutationBlocked,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.mcp_import_action))
                        }
                    }
                    Text(
                        stringResource(R.string.mcp_stdio_skip_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerCard(
    server: McpServer,
    mutationEnabled: Boolean,
    expanded: Boolean,
    capabilityState: ServerCapabilityState?,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRetryCapabilities: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // One UI 行首样式：主题蓝圆底 + 白色 glyph。
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // host 是最有辨识度的信息，放前面；空间不足时截断的是传输方式而非 host。
                    "${server.endpointHost()} · ${server.transport.displayName()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 卡片点击改为展开/折叠，编辑入口挪到独立图标保持可发现性。
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.mcp_edit_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = server.isEnabled,
                onCheckedChange = onToggleEnabled,
                enabled = mutationEnabled,
            )
        }
        AnimatedVisibility(visible = expanded) {
            McpServerCapabilities(
                capabilityState = capabilityState,
                onRetry = onRetryCapabilities,
            )
        }
    }
}

/** 展开区域：展示该 MCP 服务器声明的工具 / 资源 / 提示词。 */
@Composable
private fun McpServerCapabilities(
    capabilityState: ServerCapabilityState?,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 66.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (capabilityState) {
            null, ServerCapabilityState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    stringResource(R.string.mcp_connection_testing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is ServerCapabilityState.Failed -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    capabilityState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.mcp_capabilities_retry))
                }
            }
            is ServerCapabilityState.Loaded -> {
                val result = capabilityState.result
                CapabilitySection(
                    title = stringResource(R.string.mcp_capabilities_tools, result.tools.size),
                    entries = result.tools.map { tool ->
                        if (tool.description.isBlank()) tool.name else "${tool.name} — ${tool.description}"
                    },
                )
                if (result.resources.isNotEmpty()) {
                    CapabilitySection(
                        title = stringResource(R.string.mcp_capabilities_resources, result.resources.size),
                        entries = result.resources,
                    )
                }
                if (result.prompts.isNotEmpty()) {
                    CapabilitySection(
                        title = stringResource(R.string.mcp_capabilities_prompts, result.prompts.size),
                        entries = result.prompts,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilitySection(
    title: String,
    entries: List<String>,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    entries.forEach { entry ->
        Text(
            entry,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun McpTransport.displayName(): String = when (this) {
    McpTransport.SSE -> stringResource(R.string.mcp_transport_sse)
    McpTransport.STREAMABLE_HTTP -> stringResource(R.string.mcp_transport_streamable_http)
}

/** 从 endpoint URL 提取 host 用于列表副标题；解析失败时回退到原始 URL。 */
private fun McpServer.endpointHost(): String =
    runCatching { java.net.URI(endpointUrl).host }
        .getOrNull()
        ?.takeUnless { it.isBlank() }
        ?: endpointUrl
