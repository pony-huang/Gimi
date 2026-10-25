package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 真实 A2UI 组件树预览：基础布局可以放 Panel，Panel 内也可以放基础组件及数据模板。 */
@Preview(name = "组合容器 · 浅色", widthDp = 400, heightDp = 1900)
@Composable
fun ChatA2uiPanelLightPreview() = ChatA2uiPanelFixturePreview(dark = false)

@Preview(name = "组合容器 · 深色", widthDp = 400, heightDp = 1900)
@Composable
fun ChatA2uiPanelDarkPreview() = ChatA2uiPanelFixturePreview(dark = true)

@Preview(name = "组合容器 · 窄屏大字", widthDp = 280, heightDp = 2300, fontScale = 1.3f)
@Composable
fun ChatA2uiPanelNarrowPreview() = ChatA2uiPanelFixturePreview(dark = false)

@Preview(name = "横向自由滚动", widthDp = 400, heightDp = 1900)
@Composable
fun ChatA2uiPanelHorizontalPreview() = ChatA2uiPanelFixturePreview(dark = false, horizontal = true)

@Preview(name = "空集合", widthDp = 400, heightDp = 1900)
@Composable
fun ChatA2uiPanelEmptyPreview() = ChatA2uiPanelFixturePreview(dark = false, empty = true)

@Composable
private fun ChatA2uiPanelFixturePreview(dark: Boolean, horizontal: Boolean = false, empty: Boolean = false) {
    ChatA2uiSurfacePreview(dark, "容器可以装下你的组合") { catalogId ->
        panelPreviewMessages(catalogId, horizontal, empty)
    }
}

/** 组件预览共用的协议宿主，只负责 fixture 渲染和本地示例事件展示。 */
@Composable
internal fun ChatA2uiSurfacePreview(
    dark: Boolean,
    title: String,
    fixtures: (catalogId: String) -> List<String>,
) {
    val catalog = remember { containerPreviewCatalog() }
    val processor = remember(catalog) { A2uiMessageProcessor(listOf(catalog)) }
    val surfaces by processor.activeSurfaces.collectAsState()
    var lastEvent by remember { mutableStateOf("可滑动浏览、展开列表，或填写并提交下方示例。") }
    var protocolError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(processor) {
        val parser = A2uiMessageParser()
        var expanded = false
        launch { processor.collectMessages() }
        launch(start = CoroutineStart.UNDISPATCHED) {
            processor.outboundEvents.collect { event ->
                when (event) {
                    is A2uiClientErrorMessage -> protocolError = "${event.code}: ${event.message}"
                    is A2uiClientEventMessage -> {
                        // 示例只展示组件原有事件和提交值，不执行工具或伪装成业务操作成功。
                        lastEvent = "事件：${event.type}；内容：${event.context}"
                        if (event.type == "draft_expand") {
                            expanded = !expanded
                            processor.processInput(parser, containerPreviewEnvelope("updateComponents", buildJsonObject {
                                put("surfaceId", "news")
                                put("components", Json.parseToJsonElement("""[
                                    {"id":"root","component":"Panel","mode":"${if (expanded) "static" else "vertical"}",
                                     "header":"news-header","children":{"componentId":"news-item","path":"/items"},"footer":"news-footer"},
                                    {"id":"news-expand-label","component":"Text","text":"${if (expanded) "收起列表" else "展开全部"}"}
                                ]"""))
                            }))
                        }
                    }
                }
            }
        }
        fixtures(catalog.id).forEach { processor.processInput(parser, it) }
        awaitCancellation()
    }
    AsssistantaiTheme(darkTheme = dark) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("真实 A2UI 渲染 · 交互预览", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                surfaces.forEach { surface ->
                    key(surface.id) { ChatA2uiContent(listOf(surface), failed = false) }
                }
                Text(protocolError ?: lastEvent, style = MaterialTheme.typography.bodySmall,
                    color = if (protocolError == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 只替换正式目录的图片加载器，预览使用离线素材及相同的组件契约。 */
private fun containerPreviewCatalog(): A2uiCatalog {
    val base = chatA2uiCatalog {}
    val image = MaterialA2uiBasicCatalogV1Defaults.image { url, description, _, modifier, _ ->
        PanelFixtureImage(url.substringAfterLast('/').toIntOrNull() ?: 0, description, modifier)
    }
    return A2uiCatalog(
        catalogId = "https://gimi.app/a2ui/catalogs/preview/v1",
        components = base.components.filterNot { it.name == "Image" } + image,
        functions = base.functions,
        isInline = true,
    )
}

/** 离线图片加载 fixture；Image 本身仍使用基础组件的 schema、属性绑定和渲染入口。 */
@Composable
private fun PanelFixtureImage(index: Int, description: String?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.semantics { description?.let { contentDescription = it } }) {
        drawRect(colors.primaryContainer)
        drawCircle(colors.surface.copy(alpha = 0.8f), size.minDimension * 0.095f,
            Offset(size.width * (0.7f - index % 3 * 0.12f), size.height * 0.24f))
        val ridge = Path().apply {
            moveTo(0f, size.height * 0.58f)
            lineTo(size.width * 0.22f, size.height * (0.25f + index % 2 * 0.1f))
            lineTo(size.width * 0.46f, size.height * 0.55f)
            lineTo(size.width * 0.72f, size.height * 0.32f)
            lineTo(size.width, size.height * 0.57f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(ridge, colors.primary.copy(alpha = 0.35f))
        drawRect(colors.surfaceContainerHigh.copy(alpha = 0.72f),
            topLeft = Offset(0f, size.height * 0.7f), size = Size(size.width, size.height * 0.3f))
    }
}

/** 相册和资讯均只声明一次基础组件模板，其余条目通过标准 children 数据绑定生成。 */
internal fun panelPreviewMessages(catalogId: String, horizontal: Boolean, empty: Boolean): List<String> = buildList {
    fun addSurface(id: String, components: String, data: String) {
        add(containerPreviewEnvelope("createSurface", buildJsonObject {
            put("surfaceId", id); put("catalogId", catalogId); put("sendDataModel", true)
        }))
        add(containerPreviewEnvelope("updateDataModel", buildJsonObject {
            put("surfaceId", id); put("path", "/"); put("value", Json.parseToJsonElement(data).jsonObject)
        }))
        add(containerPreviewEnvelope("updateComponents", buildJsonObject {
            put("surfaceId", id); put("components", Json.parseToJsonElement(components).jsonArray)
        }))
    }
    addSurface("album", """[
        {"id":"root","component":"Column","children":["album-intro","album-panel"],"align":"stretch"},
        {"id":"album-intro","component":"Text","text":"基础布局中放入高级容器","variant":"caption"},
        {"id":"album-panel","component":"Panel","mode":"${if (horizontal) "horizontal" else "pages"}",
         "header":"album-header","children":{"componentId":"photo-item","path":"/photos"},"footer":"album-footer"},
        {"id":"album-header","component":"Column","children":["album-title","album-caption"],"align":"stretch"},
        {"id":"album-title","component":"Text","text":"周末的片刻","variant":"h4"},
        {"id":"album-caption","component":"Text","text":"每张图都可以有自己的标题与按钮","variant":"caption"},
        {"id":"photo-item","component":"Column","children":["photo-image","photo-title","photo-caption","photo-action"],"align":"stretch"},
        {"id":"photo-image","component":"Image","url":{"path":"image"},"description":{"path":"caption"},"variant":"header","fit":"cover"},
        {"id":"photo-title","component":"Text","text":{"path":"title"},"variant":"h4"},
        {"id":"photo-caption","component":"Text","text":{"path":"caption"},"variant":"caption"},
        {"id":"photo-action","component":"Button","variant":"default","child":"photo-action-label",
         "action":{"event":{"name":"draft_select","context":{"title":{"path":"title"}}}}},
        {"id":"photo-action-label","component":"Text","text":"选择这张"},
        {"id":"album-footer","component":"Text","text":"左右滑动，浏览更多片刻","variant":"caption"}
    ]""", if (empty) """{"photos":[]}""" else """{"photos":[
        {"image":"fixture://landscape/0","title":"湖边的清晨","caption":"薄雾、远山与水面"},
        {"image":"fixture://landscape/1","title":"树林里的光","caption":"阳光穿过树梢"},
        {"image":"fixture://landscape/2","title":"远山入画","caption":"山峦在云间层叠"},
        {"image":"fixture://landscape/3","title":"静静的岸边","caption":"沿着水边慢慢走"}
    ]}""")
    addSurface("news", """[
        {"id":"root","component":"Panel","mode":"vertical","header":"news-header",
         "children":{"componentId":"news-item","path":"/items"},"footer":"news-footer"},
        {"id":"news-header","component":"Column","children":["news-title","news-caption"],"align":"stretch"},
        {"id":"news-title","component":"Text","text":"值得一读","variant":"h4"},
        {"id":"news-caption","component":"Text","text":"资讯摘要 · 标题与操作始终可见","variant":"caption"},
        {"id":"news-item","component":"Column","children":["item-category","item-title","item-summary","item-source","item-action","item-divider"],"align":"stretch"},
        {"id":"item-category","component":"Text","text":{"path":"category"},"variant":"caption"},
        {"id":"item-title","component":"Text","text":{"path":"title"},"variant":"h4"},
        {"id":"item-summary","component":"Text","text":{"path":"summary"}},
        {"id":"item-source","component":"Text","text":{"path":"source"},"variant":"caption"},
        {"id":"item-action","component":"Button","variant":"borderless","child":"item-action-label",
         "action":{"event":{"name":"draft_select","context":{"title":{"path":"title"}}}}},
        {"id":"item-action-label","component":"Text","text":"查看详情"},
        {"id":"item-divider","component":"Divider"},
        {"id":"news-footer","component":"Row","children":["news-refresh","news-expand"],"justify":"end","align":"center"},
        {"id":"news-refresh","component":"Button","variant":"borderless","child":"news-refresh-label","action":{"event":{"name":"draft_refresh","context":{}}}},
        {"id":"news-refresh-label","component":"Text","text":"刷新"},
        {"id":"news-expand","component":"Button","variant":"default","child":"news-expand-label","action":{"event":{"name":"draft_expand","context":{}}}},
        {"id":"news-expand-label","component":"Text","text":"展开全部"}
    ]""", if (empty) """{"items":[]}""" else """{"items":[
        {"category":"设计","title":"让信息更容易被看见","summary":"从阅读顺序开始，重新组织复杂页面的内容层级。","source":"设计观察 · 12 分钟前"},
        {"category":"科技","title":"小模型也能完成更多本地任务","summary":"轻量模型与端侧工具的协作，正在改变日常应用。","source":"科技周刊 · 28 分钟前"},
        {"category":"文化","title":"在一间书店里度过下午","summary":"一本书、一杯茶，以及不用赶时间的片刻。","source":"城市来信 · 42 分钟前"},
        {"category":"生活","title":"把空闲时间还给自己","summary":"从整理通知开始，让每天的注意力少一些打扰。","source":"生活提案 · 1 小时前"},
        {"category":"科学","title":"从一片树叶读懂季节","summary":"观察叶片的颜色与形状，发现身边细小的变化。","source":"自然笔记 · 2 小时前"}
    ]}""")
    addSurface("form", """[
        {"id":"root","component":"Panel","header":"form-header","children":["name-field","description-check"],"footer":"form-actions"},
        {"id":"form-header","component":"Column","children":["form-title","form-caption"],"align":"stretch"},
        {"id":"form-title","component":"Text","text":"给卡片起个名字","variant":"h4"},
        {"id":"form-caption","component":"Text","text":"基础输入组件保留原有绑定与提交行为","variant":"caption"},
        {"id":"name-field","component":"TextField","label":"名称","value":{"path":"/name"}},
        {"id":"description-check","component":"CheckBox","label":"保留描述","value":{"path":"/includeDescription"}},
        {"id":"form-actions","component":"Row","children":["form-cancel","form-submit"],"justify":"end","align":"center"},
        {"id":"form-cancel","component":"Button","variant":"borderless","child":"form-cancel-label","action":{"event":{"name":"draft_cancel","context":{}}}},
        {"id":"form-cancel-label","component":"Text","text":"取消"},
        {"id":"form-submit","component":"Button","variant":"primary","child":"form-submit-label",
         "action":{"event":{"name":"draft_submit","context":{"name":{"path":"/name"},"includeDescription":{"path":"/includeDescription"}}}}},
        {"id":"form-submit-label","component":"Text","text":"提交示例"}
    ]""", """{"name":"周末的片刻","includeDescription":true}""")
}

internal fun containerPreviewEnvelope(operation: String, payload: JsonObject): String = buildJsonObject {
    put("version", "v0.9.1"); put(operation, payload)
}.toString()
