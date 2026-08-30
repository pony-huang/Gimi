package github.ponyhuang.gimi.feature.toolauthorization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDescriptor

/**
 * 单个工具的授权行（等宽字体名 + 描述 + 开关）。
 * 应放在 [github.ponyhuang.gimi.ui.preference.PreferenceGroupCard] 内使用，
 * 行间分隔线由 [showDivider] 控制。
 */
@Composable
fun ToolAuthorizationRow(
    tool: ToolDescriptor,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, role = Role.Switch) { onEnabledChange(!tool.isEnabled) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = tool.isEnabled,
                onCheckedChange = onEnabledChange,
                enabled = enabled,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}
