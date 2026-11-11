package github.ponyhuang.gimi.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.LocalPreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceStyleScope
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * 选择器的圆角单选项卡片行：抽屉页与选择对话框共用的「选项」统一样式。
 *
 * 卡片底色与抽屉搜索框同源（[preferenceGroupCardColor]），圆角沿用分组卡片的
 * 风格半径（Compact 16dp / Regular 24dp），点按涟漪被裁剪在卡片内；
 * 选中态在行尾以主题蓝对勾标注（与既有选择器一致，不用底色区分选中），
 * 禁用行统一按 0.38 alpha 降级文字，不用整行透明度。
 * 列表项之间的间距与相对容器的水平边距由调用方放在 modifier 上，
 * 卡片自身只负责圆角、底色、最小高度与内边距。
 *
 * @param title 选项主标题。
 * @param subtitle 副标题；空串时不渲染，保持单行行高。
 * @param secondaryTitle 跟在主标题尾部、底对齐的次要文字（如音色的原文写法）；null 不渲染。
 * @param selected 是否为当前选中项；选中行尾追加对勾。
 * @param enabled 是否可选；不可选的选项仍展示但不可点击，文字统一禁用色。
 * @param onClick 点选回调；由调用方在回调里提交选择并翻页。
 * @param leading 主标题前的图标槽（如厂商 logo）；与文字之间留 [GimiListDefaults.ContentSpacing]。
 * @param trailing 行尾自定义内容；传入时取代内置对勾，用于承载徽标、右箭头等。
 * @param role 无障碍语义角色；导航行传 [Role.Button]，选择行保持 [Role.RadioButton]。
 */
@Composable
fun GimiPickerItemRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryTitle: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    role: Role = Role.RadioButton,
) {
    val compact = LocalPreferenceStyle.current == PreferenceStyle.Compact
    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 16.dp else 24.dp))
            .background(preferenceGroupCardColor())
            .heightIn(min = GimiListDefaults.MinHeight)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = GimiListDefaults.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(GimiListDefaults.ContentSpacing))
        }
        Column(Modifier.weight(1f)) {
            // 标题与次要文字同排底对齐；weight(fill = false) 让长标题在自己的份额内省略，
            // 不把次要文字挤出卡片，短标题则按内容宽度紧挨排布。
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else disabledColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (secondaryTitle != null) {
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 12.dp)) { trailing() }
        } else if (selected) {
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

/** 预览按抽屉页面的真实底色渲染，展示选中、未选中、次要文字与禁用四种形态。 */
@Preview(name = "选项卡片 · 浅色", showBackground = true, widthDp = 390)
@Preview(
    name = "选项卡片 · 深色",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GimiPickerItemRowPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GimiPickerItemRow(
                    title = "龙安干",
                    subtitle = "女 · 默认音色",
                    selected = true,
                    onClick = {},
                )
                GimiPickerItemRow(
                    title = "龙安欢",
                    subtitle = "女 · 方言与多语种",
                    selected = false,
                    onClick = {},
                )
                GimiPickerItemRow(
                    title = "Zora",
                    secondaryTitle = "Зора",
                    subtitle = "男 · 小语种",
                    selected = false,
                    onClick = {},
                )
                GimiPickerItemRow(
                    title = "MiMo TTS",
                    subtitle = "未接入 TTS 能力",
                    selected = false,
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

/** 长标题在自身份额内省略，不把次要文字与对勾挤出卡片。 */
@Preview(name = "选项卡片 · 长文本", showBackground = true, widthDp = 390)
@Composable
private fun GimiPickerItemRowLongTextPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            Column(modifier = Modifier.padding(16.dp)) {
                GimiPickerItemRow(
                    title = "qwen-audio-3.1-realtime-plus-flash-长模型名",
                    secondaryTitle = "Zora",
                    subtitle = "千问 · Realtime · 未接入",
                    selected = true,
                    onClick = {},
                )
            }
        }
    }
}

/** Original 风格沿用更大的 24dp 圆角，与设置分组卡片保持一致。 */
@Preview(name = "选项卡片 · Original 风格", showBackground = true, widthDp = 390)
@Composable
private fun GimiPickerItemRowOriginalPreview() {
    GimiTheme {
        PreferenceStyleScope(PreferenceStyle.Original) {
            Surface(color = preferenceCanvasColor()) {
                GimiPickerItemRow(
                    title = "龙安干",
                    subtitle = "女 · 默认音色",
                    selected = true,
                    onClick = {},
                )
            }
        }
    }
}

/** 导航行形态：前置图标与自定义行尾取代内置对勾。 */
@Preview(name = "选项卡片 · 导航行", showBackground = true, widthDp = 390)
@Composable
private fun GimiPickerItemRowNavigationPreview() {
    GimiTheme {
        Surface(color = preferenceCanvasColor()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GimiPickerItemRow(
                    title = "Deepseek",
                    subtitle = "2 个模型",
                    selected = false,
                    onClick = {},
                    leading = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    role = Role.Button,
                )
            }
        }
    }
}
