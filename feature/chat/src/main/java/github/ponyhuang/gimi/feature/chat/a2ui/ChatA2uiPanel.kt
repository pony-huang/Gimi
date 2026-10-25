package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R

/**
 * 聊天宿主的通用 A2UI 容器；不定义天气、新闻或相册的数据结构。
 *
 * 头尾槽位及正文均引用同一 surface 的真实组件，模板引用保留相对数据路径。
 * 布局、主题及滚动由宿主管理，内容继续使用基础组件及其原有绑定。
 */
internal object ChatA2uiPanel : A2uiComponent {
    override val name = "Panel"
    override val description =
        "A themed container for existing components. Optional header/footer component IDs stay fixed. " +
            "children accepts component IDs or a standard data-list template. mode selects static, " +
            "horizontal, vertical, pages, or grid. No domain-specific content fields."

    private val headerProperty = A2uiProperty.componentId("header")
    private val footerProperty = A2uiProperty.componentId("footer")
    private val childrenProperty = A2uiProperty.childList("children", required = true)
    private val modeProperty = A2uiProperty.enum(
        key = "mode",
        enumValues = PanelMode.entries,
        mapToString = { it.wireValue },
        convertFromString = { value -> PanelMode.entries.firstOrNull { it.wireValue == value } ?: PanelMode.Static },
        defaultValue = PanelMode.Static,
    )
    override val properties = listOf(
        headerProperty, childrenProperty, footerProperty, modeProperty,
        A2uiBasicCatalogV1.WeightProperty, A2uiBasicCatalogV1.Card.AccessibilityProperty,
    )

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bindChildReferences(childrenProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val children = properties.bindChildReferences(childrenProperty) ?: return
        val header = properties[headerProperty]
        val footer = properties[footerProperty]
        val mode = properties[modeProperty] ?: PanelMode.Static
        val accessibility = properties.bind(A2uiBasicCatalogV1.Card.AccessibilityProperty)
        val scope = this
        Surface(
            modifier = modifier.fillMaxWidth().chatA2uiContainerAccessibility(accessibility),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                header?.let { ChatA2uiChild(scope, A2uiComponentReference(it)) }
                ChatA2uiPanelBody(scope, children, mode)
                footer?.let {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ChatA2uiChild(scope, A2uiComponentReference(it))
                }
            }
        }
    }
}

/** 容器只选择布局行为，内部内容始终由目录中的子组件解释。 */
private enum class PanelMode(val wireValue: String) {
    Static("static"), Horizontal("horizontal"), Vertical("vertical"), Pages("pages"), Grid("grid"),
}

@Composable
private fun ChatA2uiPanelBody(scope: A2uiComponentScope, children: List<A2uiComponentReference>, mode: PanelMode) {
    if (children.isEmpty()) {
        Text(stringResource(R.string.chat_a2ui_collection_empty), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    when (mode) {
        PanelMode.Static -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            children.forEach { reference ->
                key(reference.id, reference.baseDataPath) { ChatA2uiChild(scope, reference) }
            }
        }
        PanelMode.Horizontal -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(children, key = { index, ref -> "${ref.id}:${ref.baseDataPath}:$index" }) { _, ref ->
                    Box(Modifier.width(itemWidth)) { ChatA2uiChild(scope, ref) }
                }
            }
        }
        PanelMode.Vertical -> {
            val state = rememberLazyListState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 外层聊天也会纵向滚动，因此正文限定视口，头尾不放进懒列表。
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    state = state,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(children, key = { index, ref -> "${ref.id}:${ref.baseDataPath}:$index" }) { _, ref ->
                        ChatA2uiChild(scope, ref)
                    }
                }
                Text(stringResource(R.string.chat_a2ui_position_vertical, state.firstVisibleItemIndex + 1, children.size),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PanelMode.Pages -> {
            val state = rememberPagerState(pageCount = { children.size })
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalPager(
                    state = state,
                    contentPadding = PaddingValues(end = 24.dp),
                    pageSpacing = 12.dp,
                    key = { index -> "${children[index].id}:${children[index].baseDataPath}:$index" },
                ) { index -> ChatA2uiChild(scope, children[index]) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(stringResource(R.string.chat_a2ui_position_horizontal, state.currentPage + 1, children.size),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        PanelMode.Grid -> {
            // 列数由可用宽度决定；大字号同步提高单元格最小宽度，避免文字与操作挤在一起。
            val minCellWidth = 128.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minCellWidth),
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                gridItemsIndexed(children, key = { index, ref -> "${ref.id}:${ref.baseDataPath}:$index" }) { _, ref ->
                    ChatA2uiChild(scope, ref)
                }
            }
        }
    }
}
