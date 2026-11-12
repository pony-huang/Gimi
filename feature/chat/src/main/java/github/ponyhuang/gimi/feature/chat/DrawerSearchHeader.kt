package github.ponyhuang.gimi.feature.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.BrandBlue

/** 搜索图标原位扩展为输入容器；状态由调用方持有，聚焦和输入法由 Route 响应展开完成事件。 */
@Composable
internal fun DrawerSearchHeader(
    expanded: Boolean,
    query: String,
    enabled: Boolean,
    selecting: Boolean,
    focusRequester: FocusRequester,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onExpanded: () -> Unit,
    onSearch: () -> Unit,
) {
    val transition = updateTransition(expanded, label = "drawer search")
    val progress by transition.animateFloat(
        transitionSpec = { tween(280, easing = FastOutSlowInEasing) },
        label = "search container expansion",
    ) { if (it) 1f else 0f }
    val latestOnExpanded by rememberUpdatedState(onExpanded)
    var hasFocus by remember { mutableStateOf(false) }
    val editable = enabled && expanded && !selecting && !transition.isRunning
    val searchDescription = stringResource(R.string.chat_drawer_search)

    // 使用过渡的实际完成状态；中途收起会取消此次聚焦，避免键盘在退出后迟到。
    LaunchedEffect(transition.currentState, transition.targetState, transition.isRunning) {
        if (transition.currentState && transition.targetState && !transition.isRunning) latestOnExpanded()
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val containerWidth = 48.dp + (maxWidth - 48.dp) * progress
        val containerHeight = 48.dp + 8.dp * progress
        Box(
            Modifier.width(containerWidth).height(containerHeight).clip(CircleShape)
                .background(preferenceGroupCardColor())
                .then(
                    if (progress > 0f) Modifier.border(
                        width = if (hasFocus) 2.dp else 1.dp,
                        color = if (hasFocus) BrandBlue else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ) else Modifier,
                ),
        ) {
            if (transition.currentState || transition.targetState) {
                Row(
                    Modifier.fillMaxSize().graphicsLayer { alpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, enabled = enabled && expanded && !selecting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_add_to_chat_back))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        enabled = enabled,
                        readOnly = !editable,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester)
                            .focusProperties { canFocus = editable }
                            .onFocusChanged { hasFocus = it.isFocused }
                            .semantics { contentDescription = searchDescription },
                        decorationBox = { input ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text(
                                    searchDescription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                input()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }, enabled = editable) {
                            Icon(Icons.Default.Close, stringResource(R.string.chat_drawer_clear_search))
                        }
                    }
                }
            }
            if (progress < 1f) {
                IconButton(
                    onClick = onOpen,
                    enabled = enabled && !expanded && !selecting,
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp)
                        .graphicsLayer { alpha = 1f - progress },
                ) {
                    Icon(Icons.Default.Search, searchDescription, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
