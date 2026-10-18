package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.feature.chat.R

/** 聊天卡片标题统一使用紧凑层级，避免生成 h1/h2 时套用大标题及过量留白。 */
internal object ChatA2uiText : A2uiBasicCatalogV1.Text {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        text: String,
        variant: A2uiBasicCatalogV1.Text.Variant,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val isHeading = variant != A2uiBasicCatalogV1.Text.Variant.Body &&
            variant != A2uiBasicCatalogV1.Text.Variant.Caption
        val style = when (variant) {
            A2uiBasicCatalogV1.Text.Variant.Body -> MaterialTheme.typography.bodyLarge
            A2uiBasicCatalogV1.Text.Variant.Caption -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.titleMedium
        }
        Text(
            text = text,
            style = style,
            modifier = modifier.padding(vertical = if (isHeading) 2.dp else 0.dp).semantics {
                if (isHeading) heading()
                if (!accessibility?.label.isNullOrBlank() || !accessibility?.description.isNullOrBlank()) {
                    contentDescription = listOfNotNull(accessibility?.label, text, accessibility?.description)
                        .filter { it.isNotBlank() }.distinct().joinToString(" - ")
                }
            },
        )
    }
}

/** 仅替换目录中的卡片外观，子组件仍由官方运行时解析和绑定。 */
internal object ChatA2uiCard : A2uiBasicCatalogV1.Card {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val state = observeA2uiComponentState(childId)
        ChatA2uiCardFrame(modifier.chatAccessibility(accessibility)) {
            when (state) {
                is A2uiComponentState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                is A2uiComponentState.Error -> Text(stringResource(R.string.chat_a2ui_failed),
                    color = MaterialTheme.colorScheme.error)
                is A2uiComponentState.Success -> A2uiComponent(component = state.component)
            }
        }
    }
}

/** 保留官方 action/checks 合约，用低圆角按钮替代默认胶囊形按钮。 */
internal object ChatA2uiButton : A2uiBasicCatalogV1.Button {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String,
        variant: A2uiBasicCatalogV1.Button.Variant,
        action: Map<String, Any?>,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        val state = observeA2uiComponentState(childId)
        val failedCheck = checks.firstOrNull { !it.condition }
        val currentAction by rememberUpdatedState(action)
        Column(modifier) {
            ChatA2uiActionButton(
                variant = variant,
                enabled = state is A2uiComponentState.Success && failedCheck == null,
                onClick = { dispatchAction(currentAction) },
                modifier = Modifier.chatAccessibility(accessibility, clickable = true).semantics {
                    failedCheck?.message?.takeIf { it.isNotBlank() }?.let { error(it) }
                },
            ) {
                when (state) {
                    is A2uiComponentState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                    is A2uiComponentState.Error -> Text(stringResource(R.string.chat_a2ui_failed),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    is A2uiComponentState.Success -> {
                        val component = state.component
                        if (component.type == "Text") {
                            // 按钮标签沿用原始绑定，仅限制排版，避免窄屏时逐字换行。
                            val label = with(component.scope) {
                                component.properties.bind(A2uiBasicCatalogV1.Text.TextProperty)
                            }.orEmpty()
                            val textAccessibility = with(component.scope) {
                                component.properties.bind(A2uiBasicCatalogV1.Text.AccessibilityProperty)
                            }
                            Text(label, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics {
                                    textAccessibility?.let {
                                        contentDescription = listOfNotNull(label, it.label, it.description)
                                            .filter { part -> part.isNotBlank() }.joinToString(" - ")
                                    }
                                })
                        } else {
                            A2uiComponent(component = component)
                        }
                    }
                }
            }
            failedCheck?.message?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ChatA2uiCardFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 1.dp else 2.dp,
        ),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun ChatA2uiActionButton(
    variant: A2uiBasicCatalogV1.Button.Variant,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val buttonModifier = modifier.padding(vertical = 4.dp).heightIn(min = 48.dp)
    val shape = RoundedCornerShape(8.dp)
    when (variant) {
        A2uiBasicCatalogV1.Button.Variant.Primary -> Button(onClick, buttonModifier, enabled,
            shape = shape, content = content)
        A2uiBasicCatalogV1.Button.Variant.Secondary -> OutlinedButton(onClick, buttonModifier, enabled,
            shape = shape, content = content)
        A2uiBasicCatalogV1.Button.Variant.Borderless -> TextButton(onClick, buttonModifier, enabled,
            shape = shape, content = content)
    }
}

/** 保留目录提供的读屏标签和操作提示，不覆盖控件原有点击行为。 */
private fun Modifier.chatAccessibility(
    accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
    clickable: Boolean = false,
): Modifier = if (accessibility == null) this else semantics {
    val label = if (clickable) accessibility.label else
        listOfNotNull(accessibility.label, accessibility.description).filter { it.isNotBlank() }.joinToString(" - ")
    label?.takeIf { it.isNotBlank() }?.let { contentDescription = it }
    if (clickable) accessibility.description?.takeIf { it.isNotBlank() }?.let { onClick(label = it, action = null) }
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, widthDp = 280, fontScale = 1.3f)
@Composable
private fun ChatA2uiCardStylePreview() {
    AsssistantaiTheme {
        ChatA2uiTheme {
            Column(Modifier.padding(16.dp)) {
                ChatA2uiCardFrame {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("自定义闹钟", style = MaterialTheme.typography.titleMedium)
                        Text("选择时间，确认后设定闹钟。", style = MaterialTheme.typography.bodyLarge)
                        Text("今天 · 30 分钟后", style = MaterialTheme.typography.bodySmall)
                        Text("09:37", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField("", {}, label = { Text("标签（可选）") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        ChatA2uiActionButton(A2uiBasicCatalogV1.Button.Variant.Secondary, true, {}) {
                            Text("30 分钟后", style = MaterialTheme.typography.labelLarge)
                        }
                        ChatA2uiActionButton(A2uiBasicCatalogV1.Button.Variant.Primary, true, {}) {
                            Text("设定闹钟", style = MaterialTheme.typography.labelLarge)
                        }
                        ChatA2uiActionButton(A2uiBasicCatalogV1.Button.Variant.Primary, false, {}) {
                            Text("等待确认", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
