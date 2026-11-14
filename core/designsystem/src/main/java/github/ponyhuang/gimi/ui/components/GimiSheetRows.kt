package github.ponyhuang.gimi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.LocalPreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

/**
 * 抽屉中用于互斥选择的紧凑选项行。
 *
 * @param icon 可选的语义图标；与其它抽屉菜单行共用同一槽位尺寸与禁用态着色。
 * 不传时保持原有纯文字排版，供既有调用方继续使用。
 * @param description 副标题；空串时不渲染，保持单行行高（例如未在使用中的设备）。
 */
@Composable
fun GimiSheetOptionRow(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val compact = LocalPreferenceStyle.current == PreferenceStyle.Compact
    val contentColor = when {
        compact && !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected && !compact -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GimiListDefaults.MinHeight)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = GimiListDefaults.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(GimiListDefaults.LeadingSize).padding(2.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) GimiListDefaults.ContentSpacing else 0.dp),
        ) {
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = contentColor,
            )
            // 无说明文字的行不渲染空 Text，避免占一行高度（例如未在使用中的设备）。
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (compact && !enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    else if (selected && !compact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp).size(24.dp),
            )
        }
    }
}
