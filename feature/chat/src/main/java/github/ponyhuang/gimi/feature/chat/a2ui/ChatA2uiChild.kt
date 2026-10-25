package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R

/** 容器共用官方组件路由，保留模板作用域、表单绑定、事件及加载/错误状态。 */
@Composable
internal fun ChatA2uiChild(
    scope: A2uiComponentScope,
    reference: A2uiComponentReference,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    when (val state = scope.observeA2uiComponentState(reference)) {
        is A2uiComponentState.Success -> A2uiComponent(state.component, modifier)
        is A2uiComponentState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        is A2uiComponentState.Error -> Text(stringResource(R.string.chat_a2ui_failed), modifier = modifier,
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 沿用目录的无障碍描述，不把容器内的可操作子组件合并为一个节点。 */
internal fun Modifier.chatA2uiContainerAccessibility(
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
): Modifier = if (accessibility == null) this else semantics {
    listOfNotNull(accessibility.label, accessibility.description)
        .filter(String::isNotBlank).distinct().joinToString(" · ")
        .takeIf(String::isNotBlank)?.let { contentDescription = it }
}
