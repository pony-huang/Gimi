package github.ponyhuang.gimi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * 抽屉中用于独立开关的紧凑选项行。
 *
 * 用于本地工具、官方内置工具和 MCP 等可独立启停的配置，保持与互斥选项相同的
 * 留白、图标和文字层级。
 * @param leadingIcon 自定义图标槽位；提供时替换默认图标及其圆形背景。
 */
@Composable
fun GimiSheetSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (Modifier) -> Unit)? = null,
) {
    val compact = LocalPreferenceStyle.current == PreferenceStyle.Compact
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GimiListDefaults.MinHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = if (compact) 16.dp else 20.dp, vertical = GimiListDefaults.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon(Modifier.size(24.dp))
        } else if (compact && icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(GimiListDefaults.LeadingSize).padding(2.dp),
            )
        } else if (icon != null) Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (compact) GimiListDefaults.ContentSpacing else 12.dp, end = 12.dp),
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (!compact || enabled) 1f else 0.38f),
                maxLines = if (compact) 2 else Int.MAX_VALUE,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (!compact || enabled) 1f else 0.38f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        GimiSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}
