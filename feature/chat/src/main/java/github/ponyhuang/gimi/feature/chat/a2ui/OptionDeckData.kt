package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import java.net.URI

/**
 * 方案卡组中的单个候选项。
 *
 * @property id 稳定唯一标识，回传接受/拒绝事件时作为 context 主键。
 * @property title 候选项短名称，最多展示两行。
 * @property meta 标题上方的元信息短文案，例如时长、预算或版本；为空时隐藏。
 * @property description 方案摘要，最多展示四行，超出部分省略。
 * @property tags 特征标签短语，最多消费 3 个。
 * @property emoji 无图时的占位符号；与 [imageUrl] 同时缺省时使用通用图标占位。
 * @property imageUrl 图片地址，存在时优先于 [emoji] 渲染。
 * @property link 当前方案的外部详情入口；为空时隐藏链接行。
 */
internal data class OptionDeckItem(
    val id: String,
    val title: String,
    val meta: String?,
    val description: String?,
    val tags: List<String>,
    val emoji: String?,
    val imageUrl: String?,
    val link: OptionDeckLink?,
) {
    companion object {
        const val MAX_TAGS = 3

        /** 解析单张候选卡；缺少 id 或 title 时返回 `null` 交给 A2UI 错误恢复链路。 */
        fun fromA2ui(value: Any?): OptionDeckItem? {
            val map = value.asStringMap() ?: return null
            val id = map.string("id") ?: return null
            val title = map.string("title") ?: return null
            return OptionDeckItem(
                id = id,
                title = title,
                meta = map.string("meta"),
                description = map.string("description"),
                tags = map.list("tags")
                    .filterIsInstance<String>()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .take(MAX_TAGS),
                emoji = map.string("emoji"),
                imageUrl = map.string("imageUrl"),
                link = OptionDeckLink.fromA2ui(map["link"]),
            )
        }
    }
}

/**
 * 候选项的可信外部详情链接。
 *
 * @property label 由 Agent 按当前业务语境生成的跳转文案，例如“查看路线”“打开设计稿”。
 * @property url 仅允许 http/https 的绝对地址。
 */
internal data class OptionDeckLink(
    val label: String,
    val url: String,
) {
    companion object {
        private val allowedSchemes = setOf("http", "https")

        /** 解析链接；无效地址或协议只丢弃链接入口，不让整张方案卡失效。 */
        fun fromA2ui(value: Any?): OptionDeckLink? {
            val map = value.asStringMap() ?: return null
            val label = map.string("label") ?: return null
            val url = map.string("url") ?: return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in allowedSchemes || uri.host.isNullOrBlank()) return null
            return OptionDeckLink(label = label, url = url)
        }
    }
}

/**
 * 方案卡组的输入：一组待比较的候选项，以及当前场景使用的两种决定文案。
 *
 * @property title 卡组标题，展示在进度条上方；为空时使用宿主默认文案。
 * @property acceptLabel 积极的业务动作文案，例如“想去”“赞同”“喜欢”。
 * @property rejectLabel 消极的业务动作文案，例如“换个地方”“不赞同”“不喜欢”。
 * @property items 候选卡列表，按翻阅顺序排列，2..12 张且 id 不得重复。
 */
internal data class OptionDeckData(
    val title: String?,
    val acceptLabel: String,
    val rejectLabel: String,
    val items: List<OptionDeckItem>,
) {
    companion object {
        const val MIN_ITEMS = 2
        const val MAX_ITEMS = 12

        /**
         * 将 A2UI 动态对象解析为强类型卡组数据，并在入口处执行数量与 id 唯一性约束。
         *
         * 候选少于 [MIN_ITEMS] 或出现重复 id 时返回 `null`，让 A2UI 运行时向模型报告
         * 结构错误并保留现有错误恢复链路；超过 [MAX_ITEMS] 时按顺序截断，与天气组件
         * 的上限执行方式保持一致。
         */
        fun fromA2ui(value: Any): OptionDeckData? {
            if (value is OptionDeckData) return value
            val map = value.asStringMap() ?: return null
            val acceptLabel = map.string("acceptLabel") ?: return null
            val rejectLabel = map.string("rejectLabel") ?: return null
            val items = map.list("items")
                .mapNotNull { OptionDeckItem.fromA2ui(it) }
                .take(MAX_ITEMS)
            if (items.size < MIN_ITEMS) return null
            val ids = items.mapTo(HashSet()) { it.id }
            if (ids.size != items.size) return null
            return OptionDeckData(
                title = map.string("title")?.takeIf(String::isNotEmpty),
                acceptLabel = acceptLabel,
                rejectLabel = rejectLabel,
                items = items,
            )
        }
    }
}

private val deckLinkSchema = A2uiObjectSchema(
    description = "External detail destination for this option, with context-specific action copy.",
    properties = mapOf(
        "label" to A2uiStringSchema(
            "Short link label appropriate to the option type, such as View route, Open design, or View menu.",
        ),
        "url" to A2uiStringSchema("Absolute http/https URL."),
    ),
    required = setOf("label", "url"),
)

private val deckItemSchema = A2uiObjectSchema(
    description = "One candidate option in the deck.",
    properties = mapOf(
        "id" to A2uiStringSchema(
            "Stable unique identifier within the deck; used as the key of decision events.",
        ),
        "title" to A2uiStringSchema("Short display name of the option."),
        "meta" to A2uiStringSchema(
            "Short trailing metadata such as duration, budget, or version.",
        ),
        "description" to A2uiStringSchema(
            "Concise plan summary; shown on at most four lines before ellipsis.",
        ),
        "tags" to A2uiArraySchema(
            description = "Short trait labels, at most 3 items.",
            items = A2uiStringSchema(),
            maxItems = OptionDeckItem.MAX_TAGS,
        ),
        "emoji" to A2uiStringSchema(
            "Single emoji used as placeholder artwork when no image is available.",
        ),
        "imageUrl" to A2uiStringSchema("Optional image URL; preferred over emoji when present."),
        "link" to deckLinkSchema,
    ),
    required = setOf("id", "title"),
)

private val deckPayloadSchema = A2uiObjectSchema(
    description = "Unified horizontal deck payload for plans, recommendations, social posts or place search results. " +
        "The two action labels must match the current domain.",
    properties = mapOf(
        "title" to A2uiStringSchema(
            "Optional deck heading above the progress bar; omit for the default host label.",
        ),
        "acceptLabel" to A2uiStringSchema(
            "Positive decision label in the user's language and domain context, such as Want to go, Agree, or Like.",
        ),
        "rejectLabel" to A2uiStringSchema(
            "Negative decision label in the user's language and domain context, such as Try another, Disagree, or Dislike.",
        ),
        "items" to A2uiArraySchema(
            description = "Options in browsing order, at least 2 and at most 12, with unique ids.",
            items = deckItemSchema,
            minItems = OptionDeckData.MIN_ITEMS,
            maxItems = OptionDeckData.MAX_ITEMS,
        ),
    ),
    required = setOf("acceptLabel", "rejectLabel", "items"),
)

/**
 * OptionDeck 组件的 A2UI 属性定义。
 *
 * schema 要求场景化动作文案、至少 2 个唯一候选项；运行时解析在 [OptionDeckData.fromA2ui]
 * 再次执行同样的约束。属性同时允许内联卡组对象、数据模型绑定和函数调用三种动态值形态。
 */
internal val OptionDeckA2uiProperty = A2uiProperty.dynamicCustom(
    key = "deck",
    schema = A2uiAnySchema(
        description = "Complete option deck object or a dynamic binding such as {\"path\":\"/deck\"}.",
        keywords = listOf(
            A2uiSchemaKeyword.OneOf(
                listOf(
                    deckPayloadSchema,
                    A2uiDataBindingSchema.DEFAULT_INSTANCE,
                    A2uiFunctionCallSchema.DEFAULT_INSTANCE,
                ),
            ),
        ),
    ),
    safeCast = { OptionDeckData.fromA2ui(it) },
    required = true,
)

private fun Any?.asStringMap(): Map<*, *>? = this as? Map<*, *>

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)

private fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: emptyList<Any?>()
