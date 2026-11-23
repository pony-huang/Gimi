package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.LocalPreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 无说明文字的行不渲染空 Text，避免占一行高度（例如未在使用中的设备）。
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (compact && !enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    else if (selected && !compact) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

// ── 预览 ────────────────────────────────────────────────────

@Preview(name = "抽屉选项行 · 浅色", showBackground = true, widthDp = 390)
@Preview(name = "抽屉选项行 · 深色", showBackground = true, widthDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GimiSheetOptionRowPreview() {
    GimiTheme {
        PreferenceStyleScope(PreferenceStyle.Compact) {
            Surface(color = preferenceCanvasColor()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GimiSheetOptionRow(
                        selected = true,
                        enabled = true,
                        label = "完整自动授权模式",
                        description = "允许 Agent 自动执行所有操作，跳过确认",
                        onClick = {},
                    )
                    GimiSheetOptionRow(
                        selected = false,
                        enabled = true,
                        label = "标准安全询问模式",
                        description = "每次使用工具时在对话中等待手动确认",
                        onClick = {},
                    )
                    GimiSheetOptionRow(
                        selected = false,
                        enabled = false,
                        label = "不可选模式（禁用态）",
                        description = "当前环境不支持此授权选项",
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Preview(name = "抽屉选项行 · 长文本单行省略", showBackground = true, widthDp = 390)
@Composable
private fun GimiSheetOptionRowLongTextPreview() {
    GimiTheme {
        PreferenceStyleScope(PreferenceStyle.Compact) {
            Surface(color = preferenceCanvasColor()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GimiSheetOptionRow(
                        selected = true,
                        enabled = true,
                        label = "这是一段非常非常长的抽屉选项标题文案仅能占一行超过限度省略测试",
                        description = "这是一段超长的副说明文案同样只能占一行超过限度在此省略截断测试",
                        onClick = {},
                    )
                }
            }
        }
    }
}
