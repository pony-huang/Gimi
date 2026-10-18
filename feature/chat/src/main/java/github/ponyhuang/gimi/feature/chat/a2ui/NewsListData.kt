package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema

/** 新闻列表头部可选的语义图标；未配置时保持默认新闻图标。 */
internal enum class NewsListIcon {
    NEWSPAPER,
    ARTICLE,
    GLOBE,
    TRENDING,
    CAMPAIGN,
    BOLT,
    ;

    companion object {
        fun fromWire(value: String?): NewsListIcon = when (value?.trim()?.lowercase()) {
            "article" -> ARTICLE
            "globe", "public" -> GLOBE
            "trending", "trending_up" -> TRENDING
            "campaign" -> CAMPAIGN
            "bolt", "breaking" -> BOLT
            else -> NEWSPAPER
        }
    }
}

/**
 * 单条新闻/资讯。
 *
 * @property title 标题，必填。
 * @property source 来源媒体。
 * @property timeLabel 发布时间短标签，例如 `12:08` 或 `10 分钟前`。
 * @property category 栏目短标签，例如科技、财经。
 * @property imageUrl 缩略图 URL。
 * @property url 原文链接，用于点击打开。
 */
internal data class NewsArticle(
    val title: String,
    val source: String?,
    val timeLabel: String?,
    val category: String?,
    val imageUrl: String?,
    val url: String?,
) {
    companion object {
        fun fromA2ui(value: Any?): NewsArticle? {
            val map = value as? Map<*, *> ?: return null
            return NewsArticle(
                title = map.string("title") ?: return null,
                source = map.string("source"),
                timeLabel = map.string("timeLabel"),
                category = map.string("category"),
                imageUrl = map.string("imageUrl"),
                url = map.string("url"),
            )
        }
    }
}

/**
 * 新闻列表统一数据。
 *
 * @property heading 列表标题；为空时组件使用本地化的默认标题。
 * @property updatedAt 更新时间短标签。
 * @property items 新闻条目，按编辑顺序排列；组件固定显示 5 条，其余内部滚动。
 * @property icon 头部语义图标；默认 [NewsListIcon.NEWSPAPER]。
 */
internal data class NewsListData(
    val heading: String?,
    val updatedAt: String?,
    val items: List<NewsArticle>,
    val icon: NewsListIcon = NewsListIcon.NEWSPAPER,
) {
    companion object {
        const val MAX_ITEMS = 20

        fun fromA2ui(value: Any): NewsListData? {
            if (value is NewsListData) return value
            val map = value as? Map<*, *> ?: return null
            val items = (map["items"] as? List<*>)
                .orEmpty()
                .mapNotNull { NewsArticle.fromA2ui(it) }
                .take(MAX_ITEMS)
            if (items.isEmpty()) return null
            return NewsListData(
                heading = map.string("heading"),
                updatedAt = map.string("updatedAt"),
                items = items,
                icon = NewsListIcon.fromWire(map.string("icon")),
            )
        }
    }
}

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)

private val newsArticleSchema = A2uiObjectSchema(
    description = "One news article. Title is required; preserve source links and metadata exactly.",
    properties = mapOf(
        "title" to A2uiStringSchema("Article headline."),
        "source" to A2uiStringSchema("Publisher or source name."),
        "timeLabel" to A2uiStringSchema("Short published-at label shown in the list."),
        "category" to A2uiStringSchema("Short section label, for example Technology or Finance."),
        "imageUrl" to A2uiStringSchema("Optional article thumbnail URL."),
        "url" to A2uiStringSchema("Canonical article URL opened when the row is tapped."),
    ),
    required = setOf("title"),
)

private val newsListSchema = A2uiObjectSchema(
    description = "A news or information digest. The component shows five rows and scrolls internally.",
    properties = mapOf(
        "heading" to A2uiStringSchema("Optional digest heading."),
        "updatedAt" to A2uiStringSchema("Short update time label."),
        "icon" to A2uiStringSchema(
            "Optional header icon: newspaper, article, globe, trending, campaign, or bolt. " +
                "Defaults to newspaper.",
        ),
        "items" to A2uiArraySchema(
            description = "Articles in editorial order, at most 20.",
            items = newsArticleSchema,
            minItems = 1,
            maxItems = NewsListData.MAX_ITEMS,
        ),
    ),
    required = setOf("items"),
)

/**
 * 新闻列表组件使用的 A2UI 动态属性，支持内联对象、数据模型绑定和函数调用。
 */
internal val NewsListA2uiProperty = A2uiProperty.dynamicCustom(
    key = "news",
    schema = A2uiAnySchema(
        description = "News digest object or a dynamic binding such as {\"path\":\"/news\"}.",
        keywords = listOf(
            A2uiSchemaKeyword.OneOf(
                listOf(
                    newsListSchema,
                    A2uiDataBindingSchema.DEFAULT_INSTANCE,
                    A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                ),
            ),
        ),
    ),
    safeCast = { NewsListData.fromA2ui(it) },
    required = true,
)
