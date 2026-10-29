package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.ProvideActionInterceptor
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R

/** 折叠容器的协议容器：摘要与正文均是组件引用，展开只改变本地展示状态。 */
internal object ChatA2uiDisclosure : A2uiComponent {
    override val name = "Disclosure"
    override val description =
        "An expandable container. summary is a component ID; children is a standard child list or template. " +
            "initiallyExpanded seeds local state. Toggling never sends a server event."
    private val summaryProperty = A2uiProperty.componentId("summary", required = true)
    private val childrenProperty = A2uiProperty.childList("children", required = true)
    private val expandedProperty = A2uiProperty.booleanWithDefault("initiallyExpanded", false)
    private val accessibilityProperty = A2uiBasicCatalogV1.Card.AccessibilityProperty
    override val properties = listOf(
        summaryProperty, childrenProperty, expandedProperty, accessibilityProperty, A2uiBasicCatalogV1.WeightProperty,
    )

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties[summaryProperty] != null && properties.bindChildReferences(childrenProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val summary = properties[summaryProperty] ?: return
        val children = properties.bindChildReferences(childrenProperty) ?: return
        var expanded by rememberSaveable { mutableStateOf(properties[expandedProperty] ?: false) }
        val contentState = rememberSaveableStateHolder()
        val stateLabel = stringResource(if (expanded) R.string.chat_a2ui_expanded else R.string.chat_a2ui_collapsed)
        val scope = this
        Surface(modifier.fillMaxWidth().chatA2uiContainerAccessibility(properties.bind(accessibilityProperty)),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { ChatA2uiChild(scope, A2uiComponentReference(summary)) }
                    IconButton(onClick = { expanded = !expanded },
                        modifier = Modifier.semantics { stateDescription = stateLabel }) {
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.chat_a2ui_collapse else R.string.chat_a2ui_expand,
                            ))
                    }
                }
                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    contentState.SaveableStateProvider("content") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            children.forEach { reference ->
                                key(reference.id, reference.baseDataPath) { ChatA2uiChild(scope, reference) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分组切换容器实现：每组引用已有组件树，局部切换保留 surface 数据模型里的输入值。 */
internal object ChatA2uiTabs : A2uiBasicCatalogV1.Tabs {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        tabs: List<A2uiBasicCatalogV1.Tabs.Tab>,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        if (tabs.isEmpty()) return
        // 用子组件 ID 保持选择；更新时仅调整页签顺序，不应把用户切换到另一组内容。
        var selectedChild by rememberSaveable { mutableStateOf(tabs.first().childId) }
        val selectedIndex = tabs.indexOfFirst { it.childId == selectedChild }.coerceAtLeast(0)
        val contentState = rememberSaveableStateHolder()
        val scope = this
        Column(modifier.fillMaxWidth().chatA2uiContainerAccessibility(accessibility),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                tabs.forEachIndexed { index, tab ->
                    Tab(selected = selectedIndex == index, onClick = { selectedChild = tab.childId }, text = { Text(tab.title) })
                }
            }
            val activeTab = tabs[selectedIndex]
            contentState.SaveableStateProvider(activeTab.childId) {
                ChatA2uiChild(scope, A2uiComponentReference(activeTab.childId))
            }
        }
    }
}

/** 详情浮层仅拦截专用的本地打开/关闭事件，其他子组件业务事件继续向外传播。 */
internal object ChatA2uiDetailSheet : A2uiBasicCatalogV1.Modal {
    override val name = "DetailSheet"
    override val description =
        "A local modal detail container. trigger is an existing component with an open_detail event action. " +
            "content is the component ID of the sheet body. A close_detail event " +
            "inside the sheet closes it locally. Other actions retain normal event behavior."

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun A2uiComponentScope.TypedContent(
        triggerId: String,
        contentId: String,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        var open by rememberSaveable { mutableStateOf(false) }
        val scope = this
        ProvideActionInterceptor(onIntercept = { payload ->
            val local = (payload["event"] as? Map<*, *>)?.get("name") == "open_detail"
            if (local) open = true
            local
        }) {
            ChatA2uiChild(scope, A2uiComponentReference(triggerId), modifier)
        }
        if (open) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheetState) {
                ProvideActionInterceptor(onIntercept = { payload ->
                    val local = (payload["event"] as? Map<*, *>)?.get("name") == "close_detail"
                    if (local) open = false
                    local
                }) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()).padding(20.dp)
                        .chatA2uiContainerAccessibility(accessibility),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChatA2uiChild(scope, A2uiComponentReference(contentId))
                    }
                }
            }
        }
    }
}
