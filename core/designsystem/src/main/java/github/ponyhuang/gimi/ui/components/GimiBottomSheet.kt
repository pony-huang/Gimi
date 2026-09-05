package github.ponyhuang.gimi.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.preferenceCanvasColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 统一应用内的底部抽屉容器。
 *
 * 默认根据内容自适应高度；传入 [forcedHeight] 时固定内容区高度，适用于配置项较多、
 * 需要稳定可预期抽屉尺寸的场景。
 *
 * @param forcedHeight 可选的内容区固定高度；为空时按内容高度展示。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GimiBottomSheet(
    onDismissRequest: () -> Unit,
    forcedHeight: Dp? = null,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = preferenceCanvasColor(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (forcedHeight == null) Modifier.wrapContentHeight()
                    else Modifier.height(forcedHeight)
                ),
        ) {
            content()
        }
    }
}

/**
 * 底部抽屉的标准标题栏，提供居中标题和左侧导航操作。
 */
@Composable
fun GimiBottomSheetHeader(
    title: String,
    navigationIcon: ImageVector,
    navigationContentDescription: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationModifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = onNavigationClick,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .then(navigationModifier),
        ) {
            Icon(
                imageVector = navigationIcon,
                contentDescription = navigationContentDescription,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/**
 * 底部抽屉中用于互斥选择的紧凑选项行。
 */
@Composable
fun GimiBottomSheetOptionRow(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
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
 * 底部抽屉中用于独立开关的紧凑选项行。
 *
 * 用于本地工具、官方内置工具和 MCP 等可独立启停的配置，保持与互斥选项相同的
 * 留白、图标和文字层级。
 */
@Composable
fun GimiBottomSheetSwitchRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
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
                .padding(start = 12.dp, end = 12.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GimiBottomSheetPreview() {
    AsssistantaiTheme {
        GimiBottomSheet(onDismissRequest = {}) {
            GimiBottomSheetOptionRow(
                selected = true,
                enabled = true,
                label = "选项一",
                description = "已选中的选项描述",
                onClick = {},
            )
            GimiBottomSheetOptionRow(
                selected = false,
                enabled = true,
                label = "选项二",
                description = "未选中的选项描述",
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GimiBottomSheetHeaderPreview() {
    AsssistantaiTheme {
        GimiBottomSheetHeader(
            title = "抽屉标题",
            navigationIcon = Icons.Default.Check,
            navigationContentDescription = "返回",
            onNavigationClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GimiBottomSheetOptionRowSelectedPreview() {
    AsssistantaiTheme {
        GimiBottomSheetOptionRow(
            selected = true,
            enabled = true,
            label = "选项一",
            description = "已选中的选项描述",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GimiBottomSheetOptionRowUnselectedPreview() {
    AsssistantaiTheme {
        GimiBottomSheetOptionRow(
            selected = false,
            enabled = true,
            label = "选项二",
            description = "未选中的选项描述",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GimiBottomSheetSwitchRowPreview() {
    AsssistantaiTheme {
        GimiBottomSheetSwitchRow(
            icon = Icons.Default.Check,
            label = "开关选项",
            description = "可独立启停的配置项",
            checked = true,
            enabled = true,
            onCheckedChange = {},
        )
    }
}
