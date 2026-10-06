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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import github.ponyhuang.gimi.domain.mcp.model.McpToolParameter
import github.ponyhuang.gimi.domain.mcp.model.McpToolSummary
import github.ponyhuang.gimi.domain.mcp.model.parameters
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import androidx.compose.ui.unit.dp

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
    PreferencePageContainer(modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(server?.name ?: stringResource(if (state.isLoadingServers) R.string.mcp_tools_title else R.string.mcp_server_missing), style = MaterialTheme.typography.titleMedium)
                    if (capability is ServerCapabilityState.Loaded) {
                        Text(stringResource(R.string.mcp_tools_count, capability.result.tools.size),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
            Column(Modifier.fillMaxWidth().clickable(onClick = onToggle).semantics { stateDescription = toggleDescription }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    }
                    Text(tool.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = toggleDescription)
                }
                if (!expanded && tool.description.isNotBlank()) {
                    Text(tool.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    Text(stringResource(R.string.mcp_tool_description), style = MaterialTheme.typography.labelLarge)
                    Text(tool.description.ifBlank { stringResource(R.string.mcp_tool_description_empty) },
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.mcp_tool_parameters), style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                    val parameters = tool.parameters()
                    if (parameters.isEmpty()) {
                        Text(stringResource(if (tool.inputSchema == null) R.string.mcp_tool_schema_unavailable else R.string.mcp_tool_parameters_empty),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    parameters.forEach { ParameterRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ParameterRow(parameter: McpToolParameter) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(parameter.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(parameter.type ?: stringResource(R.string.mcp_tool_type_unspecified), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (parameter.required) R.string.mcp_tool_required else R.string.mcp_tool_optional),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (parameter.description.isNotBlank()) Text(parameter.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        parameter.defaultValue?.let { Text(stringResource(R.string.mcp_tool_default_value, it), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (parameter.enumValues.isNotEmpty()) Text(stringResource(R.string.mcp_tool_enum_values, parameter.enumValues.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
