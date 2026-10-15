package github.ponyhuang.gimi.feature.mcp

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mcp.model.McpToolParameter
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.domain.mcp.model.parameters
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 独立的工具列表；展开只显示服务器声明，不执行工具或修改启用状态。 */
@Composable
fun McpServerToolsScreen(
    serverId: String,
    state: McpSettingsUiState,
    onAction: (McpSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val server = state.servers.firstOrNull { it.id == serverId }
    val capability = state.capabilities[serverId]
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val title = server?.name ?: stringResource(
                    if (state.isLoadingServers) R.string.mcp_tools_title else R.string.mcp_server_missing,
                )
                PreferenceSectionTitle(
                    text = if (capability is ServerCapabilityState.Loaded) {
                        "$title · ${stringResource(R.string.mcp_tools_count, capability.result.tools.size)}"
                    } else title,
                )
            }
            when {
                server == null && !state.isLoadingServers -> Unit
                capability is ServerCapabilityState.Loaded -> {
                    if (capability.result.tools.isEmpty()) item { Text(stringResource(R.string.mcp_tools_empty), modifier = Modifier.padding(16.dp)) }
                    items(capability.result.tools, key = McpToolSummary::name) { tool ->
                        McpToolCard(tool, tool.name in state.expandedToolNames,
                            onToggle = { onAction(McpSettingsAction.ToggleTool(tool.name)) })
                    }
                    if (capability.result.resources.isNotEmpty()) item {
                        CapabilityNames(stringResource(R.string.mcp_capabilities_resources, capability.result.resources.size), capability.result.resources)
                    }
                    if (capability.result.prompts.isNotEmpty()) item {
                        CapabilityNames(stringResource(R.string.mcp_capabilities_prompts, capability.result.prompts.size), capability.result.prompts)
                    }
                }
                capability is ServerCapabilityState.Failed -> item {
                    Column(Modifier.padding(16.dp)) {
                        Text(localizeMcpError(capability.message), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onAction(McpSettingsAction.RefreshCapabilities(serverId)) }) {
                            Text(stringResource(R.string.mcp_capabilities_retry))
                        }
                    }
                }
                else -> item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.mcp_connection_testing))
                    }
                }
            }
        }
    }
}

@Composable
private fun McpToolCard(tool: McpToolSummary, expanded: Boolean, onToggle: () -> Unit) {
    val toggleDescription = stringResource(if (expanded) R.string.mcp_tool_collapse else R.string.mcp_tool_expand)
    PreferenceGroupCard {
        Column(Modifier.fillMaxWidth()) {
            PreferenceListItem(
                icon = Icons.Default.Build,
                iconSize = 24.dp,
                title = tool.name,
                subtitle = if (expanded) null else tool.description,
                onClick = onToggle,
                modifier = Modifier.semantics { stateDescription = toggleDescription },
                trailingContent = {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = toggleDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ToolDetailDivider()
                    Text(stringResource(R.string.mcp_tool_description), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(tool.description.ifBlank { stringResource(R.string.mcp_tool_description_empty) },
                        style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.mcp_tool_parameters), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                    val parameters = tool.parameters()
                    if (parameters.isEmpty()) {
                        Text(stringResource(if (tool.inputSchema == null) R.string.mcp_tool_schema_unavailable else R.string.mcp_tool_parameters_empty),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    parameters.forEachIndexed { index, parameter ->
                        if (index > 0) ToolDetailDivider()
                        ParameterRow(parameter)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterRow(parameter: McpToolParameter) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(parameter.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            val type = parameter.type ?: stringResource(R.string.mcp_tool_type_unspecified)
            val requirement = stringResource(if (parameter.required) R.string.mcp_tool_required else R.string.mcp_tool_optional)
            Text("$type · $requirement", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (parameter.description.isNotBlank()) Text(parameter.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        parameter.defaultValue?.let { Text(stringResource(R.string.mcp_tool_default_value, it), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (parameter.enumValues.isNotEmpty()) Text(stringResource(R.string.mcp_tool_enum_values, parameter.enumValues.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolDetailDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun CapabilityNames(title: String, names: List<String>) {
    PreferenceGroupCard {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            names.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Preview(name = "工具详情 · 浅色", showBackground = true, widthDp = 393)
@Preview(name = "工具详情 · 深色", showBackground = true, widthDp = 393,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun McpToolDetailsPreview() {
    val tool = McpToolSummary(
        name = "create_issue",
        description = "在指定仓库创建 Issue，可设置标题、正文和标签。",
        inputSchema = buildJsonObject {
            putJsonObject("properties") {
                putJsonObject("owner") {
                    put("type", "string")
                    put("description", "仓库所属用户或组织。")
                }
                putJsonObject("body") {
                    put("type", "string")
                    put("description", "Issue 正文，支持 Markdown。")
                }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("owner")) }
        },
    )
    AsssistantaiTheme {
        PreferencePageContainer(style = PreferenceStyle.Compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                McpToolCard(tool, expanded = true, onToggle = {})
                McpToolCard(tool.copy(name = "search_repositories"), expanded = false, onToggle = {})
                McpToolCard(McpToolSummary("get_me", "获取当前用户信息。", buildJsonObject {}),
                    expanded = true, onToggle = {})
                McpToolCard(McpToolSummary("unknown", ""), expanded = true, onToggle = {})
            }
        }
    }
}
