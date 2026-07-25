package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolIds
import github.ponyhuang.asssistantai.feature.modelsettings.R

@Composable
internal fun OfficialToolsSection(
    supportedTools: List<String>,
    enabledTools: Set<String>,
    onEnabledChange: (toolId: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        supportedTools.forEach { toolId ->
            val enabled = toolId in enabledTools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnabledChange(toolId, !enabled) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                ) {
                    Text(
                        text = officialToolLabel(toolId),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = officialToolDescription(toolId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { onEnabledChange(toolId, it) },
                )
            }
        }
    }
}

@Composable
private fun officialToolLabel(toolId: String): String {
    val known = officialToolLabelRes(toolId)
    return if (known != null) {
        stringResource(known)
    } else {
        stringResource(R.string.modelsettings_official_tool_unknown, toolId)
    }
}

@Composable
private fun officialToolDescription(toolId: String): String {
    val known = officialToolDescriptionRes(toolId)
    return if (known != null) {
        stringResource(known)
    } else {
        stringResource(R.string.modelsettings_official_tool_default_description)
    }
}

@StringRes
private fun officialToolLabelRes(toolId: String): Int? = when (toolId) {
    OfficialToolIds.WEB_SEARCH -> R.string.modelsettings_official_tool_web_search
    OfficialToolIds.KIMI_FORMULAS -> R.string.modelsettings_official_tool_kimi_formulas
    else -> null
}

@StringRes
private fun officialToolDescriptionRes(toolId: String): Int? = when (toolId) {
    OfficialToolIds.WEB_SEARCH -> R.string.modelsettings_official_tool_web_search_description
    OfficialToolIds.KIMI_FORMULAS -> R.string.modelsettings_official_tool_kimi_formulas_description
    else -> null
}
