package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 通用容器组合预览：折叠、列表、分组切换和详情浮层，全部使用真实基础组件。 */
@Preview(name = "容器合集 · 浅色", widthDp = 400, heightDp = 2200)
@Composable
fun ChatA2uiContainersLightPreview() = ChatA2uiContainerShowcase(dark = false)

@Preview(name = "容器合集 · 深色", widthDp = 400, heightDp = 2200)
@Composable
fun ChatA2uiContainersDarkPreview() = ChatA2uiContainerShowcase(dark = true)

@Preview(name = "折叠展开态", widthDp = 400, heightDp = 2500)
@Composable
fun ChatA2uiContainersExpandedPreview() = ChatA2uiContainerShowcase(dark = false, expanded = true)

@Preview(name = "列表 · 窄屏大字", widthDp = 280, heightDp = 2700, fontScale = 1.3f)
@Composable
fun ChatA2uiContainersNarrowPreview() = ChatA2uiContainerShowcase(dark = false)

@Preview(name = "列表 · 宽屏", widthDp = 840, heightDp = 1800)
@Composable
fun ChatA2uiContainersWidePreview() = ChatA2uiContainerShowcase(dark = false)

@Preview(name = "空文件集合", widthDp = 400, heightDp = 2000)
@Composable
fun ChatA2uiContainersEmptyGridPreview() = ChatA2uiContainerShowcase(dark = false, emptyGrid = true)

@Preview(name = "详情正文 · 浅色", widthDp = 400, heightDp = 2400)
@Composable
fun ChatA2uiContainersDetailBodyPreview() = ChatA2uiContainerShowcase(dark = false, detailBody = true)

@Preview(name = "详情正文 · 深色", widthDp = 400, heightDp = 2400)
@Composable
fun ChatA2uiContainersDetailBodyDarkPreview() = ChatA2uiContainerShowcase(dark = true, detailBody = true)

@Composable
private fun ChatA2uiContainerShowcase(
    dark: Boolean,
    expanded: Boolean = false,
    detailBody: Boolean = false,
    emptyGrid: Boolean = false,
) {
    ChatA2uiSurfacePreview(dark, "按需展开，自由组合") { catalogId ->
        containerPreviewMessages(catalogId, expanded, detailBody, emptyGrid)
    }
}

/** 正式组件的离线 fixture，不表达真实任务或文件状态，也不执行示例中的业务操作。 */
internal fun containerPreviewMessages(
    catalogId: String,
    expanded: Boolean,
    detailBody: Boolean,
    emptyGrid: Boolean,
): List<String> = buildList {
    fun addSurface(id: String, components: String, data: String = "{}") {
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
    addSurface("disclosure", """[
        {"id":"root","component":"Disclosure","summary":"summary","initiallyExpanded":$expanded,
         "children":["details","sources","note"]},
        {"id":"summary","component":"Column","children":["summary-title","summary-caption"],"align":"stretch"},
        {"id":"summary-title","component":"Text","text":"这份结果，从哪里来？","variant":"h4"},
        {"id":"summary-caption","component":"Text","text":"先看结论，展开后查看依据和来源","variant":"caption"},
        {"id":"details","component":"Text","text":"示例：内容根据三份资料整理；摘要保留主要结论，补充说明与来源按需展开。"},
        {"id":"sources","component":"Column","children":{"componentId":"source-item","path":"/sources"},"align":"stretch"},
        {"id":"source-item","component":"Row","children":["source-title","source-action"],"justify":"spaceBetween","align":"center"},
        {"id":"source-title","component":"Text","text":{"path":"title"}},
        {"id":"source-action","component":"Button","variant":"borderless","child":"source-action-label",
         "action":{"event":{"name":"draft_source","context":{"title":{"path":"title"}}}}},
        {"id":"source-action-label","component":"Text","text":"查看"},
        {"id":"note","component":"TextField","label":"补充备注","value":{"path":"/note"}}
    ]""", """{"note":"","sources":[{"title":"产品说明"},{"title":"使用记录"},{"title":"补充资料"}]}""")
    addSurface("grid", """[
        {"id":"root","component":"Column","children":["grid-header","file-list","grid-footer"],"align":"stretch"},
        {"id":"file-list","component":"Column","children":{"componentId":"file-item","path":"/files"},"align":"stretch"},
        {"id":"grid-header","component":"Column","children":["grid-title","grid-caption"],"align":"stretch"},
        {"id":"grid-title","component":"Text","text":"最近的工作文件","variant":"h4"},
        {"id":"grid-caption","component":"Text","text":"${if (emptyGrid) "暂无文件" else "4 个文件"}","variant":"caption"},
        {"id":"file-item","component":"Column","children":["file-cover","file-title","file-type","file-select"],"align":"stretch"},
        {"id":"file-cover","component":"Image","url":{"path":"cover"},"description":{"path":"title"},"variant":"mediumFeature"},
        {"id":"file-title","component":"Text","text":{"path":"title"},"variant":"h4"},
        {"id":"file-type","component":"Text","text":{"path":"info"},"variant":"caption"},
        {"id":"file-select","component":"CheckBox","label":"选择","value":{"path":"selected"}},
        {"id":"grid-footer","component":"Button","variant":"default","child":"grid-action-label",
         "action":{"event":{"name":"draft_files_confirm","context":{}}}},
        {"id":"grid-action-label","component":"Text","text":"确认选择"}
    ]""", if (emptyGrid) """{"files":[]}""" else """{"files":[
        {"cover":"fixture://files/0","title":"项目概览","info":"PDF · 2.4 MB","selected":false},
        {"cover":"fixture://files/1","title":"界面草图","info":"PNG · 840 KB","selected":true},
        {"cover":"fixture://files/2","title":"会议记录","info":"文本文档 · 18 KB","selected":false},
        {"cover":"fixture://files/3","title":"交互说明","info":"PDF · 1.1 MB","selected":false}
    ]}""")
    addSurface("tabs", """[
        {"id":"root","component":"Column","children":["task-title","task-tabs","task-note"],"align":"stretch"},
        {"id":"task-title","component":"Text","text":"手边的安排","variant":"h4"},
        {"id":"task-tabs","component":"Tabs","tabs":[{"title":"待处理","child":"pending"},{"title":"已完成","child":"completed"}]},
        {"id":"pending","component":"Column","children":["pending-list","task-name"],"align":"stretch"},
        {"id":"pending-list","component":"Column","children":{"componentId":"task-item","path":"/pending"},"align":"stretch"},
        {"id":"task-item","component":"CheckBox","label":{"path":"title"},"value":{"path":"checked"}},
        {"id":"task-name","component":"TextField","label":"下一件事","value":{"path":"/nextTask"}},
        {"id":"completed","component":"Column","children":["completed-title","completed-note"],"align":"stretch"},
        {"id":"completed-title","component":"Text","text":"整理参考资料"},
        {"id":"completed-note","component":"Text","text":"示例记录 · 今天 09:20","variant":"caption"},
        {"id":"task-note","component":"Text","text":"切换分组后，已填写的内容仍然保留。勾选仅修改示例数据。","variant":"caption"}
    ]""", """{"nextTask":"","pending":[{"title":"查看设计草案","checked":false},{"title":"整理本周想法","checked":true}]}""")
    addSurface("sheet", """[
        {"id":"root","component":"Column","align":"stretch","children":["preview-title","preview-summary","${if (detailBody) "detail-content" else "detail-sheet"}"]},
        {"id":"preview-title","component":"Text","text":"项目概览","variant":"h4"},
        {"id":"preview-summary","component":"Text","text":"PDF · 2.4 MB · 查看详情后可回到原来的浏览位置。","variant":"caption"},
        {"id":"detail-sheet","component":"DetailSheet","trigger":"open-detail","content":"detail-content"},
        {"id":"open-detail","component":"Button","variant":"default","child":"open-detail-label","action":{"event":{"name":"open_detail","context":{}}}},
        {"id":"open-detail-label","component":"Text","text":"查看文件详情"},
        {"id":"detail-content","component":"Column","children":["detail-title","detail-caption","detail-image","detail-text","detail-note","detail-actions"],"align":"stretch"},
        {"id":"detail-title","component":"Text","text":"项目概览","variant":"h4"},
        {"id":"detail-caption","component":"Text","text":"文件详情 · 示例内容","variant":"caption"},
        {"id":"detail-image","component":"Image","url":"fixture://detail/1","description":"项目概览示意封面","variant":"header"},
        {"id":"detail-text","component":"Text","text":"包含项目目标、主要工作和下一步安排。你可以在这里预览补充说明，再选择后续操作。"},
        {"id":"detail-note","component":"TextField","label":"文件备注","value":{"path":"/note"}},
        {"id":"detail-actions","component":"Row","children":["detail-close","detail-choose"],"justify":"end","align":"center"},
        {"id":"detail-close","component":"Button","variant":"borderless","child":"detail-close-label","action":{"event":{"name":"close_detail","context":{}}}},
        {"id":"detail-close-label","component":"Text","text":"关闭"},
        {"id":"detail-choose","component":"Button","variant":"primary","child":"detail-choose-label","action":{"event":{"name":"draft_file_choose","context":{"note":{"path":"/note"}}}}},
        {"id":"detail-choose-label","component":"Text","text":"选择这个文件"}
    ]""", """{"note":""}""")
}
