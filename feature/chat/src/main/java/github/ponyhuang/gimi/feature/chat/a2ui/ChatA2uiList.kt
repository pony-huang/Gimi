package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.a2ui.A2uiSurfaceDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A2UI 官方 Material List 使用 LazyColumn，嵌在聊天 LazyColumn 中会得到无限高度约束。
 * 聊天宿主改用普通 Column/Row，让列表项参与外层消息列表的测量与滚动。
 */
internal object ChatA2uiList : A2uiBasicCatalogV1.List {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: List<A2uiComponentReference>,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val listModifier = modifier.withAccessibility(accessibility)
        when (direction) {
            A2uiBasicCatalogV1.List.Direction.Vertical -> Column(
                modifier = listModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = align.horizontalAlignment,
            ) {
                children.forEach { child ->
                    key(child.id, child.baseDataPath) {
                        ListItem(child, direction, align)
                    }
                }
            }

            A2uiBasicCatalogV1.List.Direction.Horizontal -> Row(
                modifier = listModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = align.verticalAlignment,
            ) {
                children.forEach { child ->
                    key(child.id, child.baseDataPath) {
                        ListItem(child, direction, align)
                    }
                }
            }
        }
    }

    @Composable
    private fun A2uiComponentScope.ListItem(
        child: A2uiComponentReference,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
    ) {
        val state = observeA2uiComponentState(child)
        val itemModifier = remember(direction, align) {
            if (direction == A2uiBasicCatalogV1.List.Direction.Vertical &&
                align == A2uiBasicCatalogV1.List.Align.Stretch
            ) Modifier.fillMaxWidth() else Modifier
        }
        AnimatedContent(
            modifier = itemModifier,
            targetState = state,
            contentKey = { value ->
                when (value) {
                    is A2uiComponentState.Loading -> "loading"
                    is A2uiComponentState.Error -> "error"
                    is A2uiComponentState.Success -> "${child.id}:${value.component.type}"
                }
            },
            label = "ChatA2uiListItem",
        ) { value ->
            when (value) {
                is A2uiComponentState.Error ->
                    Box(itemModifier) { A2uiSurfaceDefaults.ErrorFallback(value.exception) }
                is A2uiComponentState.Loading -> Box(
                    modifier = if (direction == A2uiBasicCatalogV1.List.Direction.Vertical) {
                        Modifier.fillMaxWidth().height(48.dp)
                    } else {
                        Modifier.width(48.dp).height(48.dp)
                    },
                    contentAlignment = Alignment.Center,
                ) { A2uiSurfaceDefaults.LoadingIndicator() }
                is A2uiComponentState.Success ->
                    androidx.a2ui.compose.ui.A2uiComponent(value.component, modifier = itemModifier)
            }
        }
    }
}

private fun Modifier.withAccessibility(
    attributes: A2uiBasicCatalogV1.AccessibilityAttributes?,
): Modifier = then(
    if (attributes == null) Modifier
    else Modifier.semantics {
        val description = buildList {
            attributes.label?.takeIf(String::isNotBlank)?.let(::add)
            attributes.description?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" - ")
        if (description.isNotEmpty()) contentDescription = description
    },
)

private val A2uiBasicCatalogV1.List.Align.horizontalAlignment: Alignment.Horizontal
    get() = when (this) {
        A2uiBasicCatalogV1.List.Align.Start -> Alignment.Start
        A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterHorizontally
        A2uiBasicCatalogV1.List.Align.End -> Alignment.End
        A2uiBasicCatalogV1.List.Align.Stretch -> Alignment.Start
    }

private val A2uiBasicCatalogV1.List.Align.verticalAlignment: Alignment.Vertical
    get() = when (this) {
        A2uiBasicCatalogV1.List.Align.Start -> Alignment.Top
        A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterVertically
        A2uiBasicCatalogV1.List.Align.End -> Alignment.Bottom
        A2uiBasicCatalogV1.List.Align.Stretch -> Alignment.Top
    }
