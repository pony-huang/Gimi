package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.runtime.DynamicA2uiProperty
import androidx.a2ui.compose.runtime.StaticA2uiProperty
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiNumberSchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Gimi 聊天扩展组件的独立目录。标准组件和函数由宿主按需合并。 */
internal val chatA2uiCustomCatalog: A2uiCatalog = A2uiCatalog(
    catalogId = "https://gimi.app/a2ui/catalogs/chat-components/v1",
    components = listOf(
        component("BarChart", "Compare categorical numeric values by series. Use horizontal for long labels.",
            listOf(title, subtitle, numeric("height"), label("format"), unit, flag("stacked"),
                flag("horizontal"), label("highlight"),
                rows("data", 60, "x y", "x" to string, "y" to number, "series" to string)), ::BarChart),
        component("PieChart", "Part-to-whole. data [{label,value}]; donut:true for a ring with center total.",
            listOf(title, subtitle, numeric("height"), label("format"), unit, label("centerLabel"),
                flag("donut"),
                rows("data", 12, "label value", "label" to string, "value" to number)), ::PieChart),
        component("CandlestickChart", "OHLC price candles. data [{date,open,high,low,close}].",
            listOf(title, subtitle, numeric("height"), label("format"), unit,
                rows("data", 120, "date open high low close", "date" to string, "open" to number,
                    "high" to number, "low" to number, "close" to number)), ::CandlestickChart),
        component("WeatherCard", "Display supplied current weather, hourly and daily forecasts.",
            listOf(label("location", true), label("condition", true), label("icon"), unit,
                numeric("temp", true), numeric("high"), numeric("low"), numeric("feels"),
                numeric("humidity"), numeric("wind"),
                rows("hourly", 12, "label temp", "label" to string, "temp" to number,
                    "icon" to string, "precip" to number, required = false),
                rows("daily", 10, "label min max", "label" to string, "min" to number,
                    "max" to number, "icon" to string, "precip" to number, required = false)), ::WeatherCard),
        component("ReceiptCard", "Show an itemized receipt using supplied unit prices and an ISO currency code.",
            listOf(label("merchant", true), label("orderId"), label("date"), label("status"),
                label("currency"), label("payment"), numeric("tax"), numeric("shipping"), numeric("discount"),
                rows("items", 40, "name price", "name" to string, "price" to number,
                    "qty" to number, "note" to string)), ::ReceiptCard),
        component("StockTicker", "Display a supplied stock quote and trend. Set sample true for illustrative data.",
            listOf(label("symbol", true), label("name"), label("currency"), numeric("price", true),
                numeric("change"), numeric("changePct"), flag("sample"),
                array("series", number, 120, required = false),
                rows("stats", 12, "label value", "label" to string, "value" to string, required = false)), ::StockTicker),
        component("DataTable", "Sortable, paginated table. columns [{key,label,format?,align?}], rows are objects keyed by column key.",
            listOf(title, numeric("pageSize"),
                rows("columns", 8, "key label", "key" to string, "label" to string,
                    "format" to string, "align" to string, min = 1),
                array("rows", A2uiObjectSchema(properties = emptyMap(), isAdditionalPropertiesAllowed = true), 100)), ::DataTable),
        component("KeyValueList", "Label/value rows, e.g. specs or order details.",
            listOf(title, numeric("columns"),
                rows("items", 30, "label value", "label" to string,
                    "value" to A2uiAnySchema(keywords = listOf(A2uiSchemaKeyword.OneOf(listOf(string, number)))),
                    "hint" to string)), ::KeyValueList),
        component("Timeline", "Vertical timeline of dated events or steps with status. Use Agenda for timed schedules.",
            listOf(title, rows("items", 25, "title", "title" to string, "time" to string,
                "description" to string, "icon" to string,
                "status" to A2uiStringSchema("Use done, current, active or upcoming."))), ::Timeline),
        component("Agenda", "Calendar agenda: days with timed events. Prefer over Timeline for clock times.",
            listOf(title, rows("days", 10, "date events", "date" to string, "label" to string,
                "events" to A2uiArraySchema(items = record("title", "title" to string, "time" to string,
                    "start" to string, "end" to string, "speaker" to string,
                    "location" to string, "description" to string, "tone" to string), maxItems = 20))), ::Agenda),
        ChatMusicPlayerCard,
        ChatSwitch,
        ChatChipSelect,
        ChatEntityCard,
    ),
    isInline = true,
)

private val string get() = A2uiStringSchema.INSTANCE
private val number get() = A2uiNumberSchema.INSTANCE
private val title get() = label("title")
private val subtitle get() = label("subtitle")
private val unit get() = label("unit")

private fun label(key: String, required: Boolean = false) =
    A2uiProperty.dynamicString(key = key, required = required)

private fun numeric(key: String, required: Boolean = false) =
    A2uiProperty.dynamicNumber(key = key, required = required)

private fun flag(key: String) = A2uiProperty.boolean(key = key)

private fun record(required: String, vararg fields: Pair<String, A2uiSchema>) =
    A2uiObjectSchema(
        properties = mapOf(*fields),
        required = required.split(' ').toSet(),
        isAdditionalPropertiesAllowed = false,
    )

private fun rows(
    key: String, max: Int, fieldsRequired: String, vararg fields: Pair<String, A2uiSchema>,
    min: Int = 0, required: Boolean = true,
) = array(key, record(fieldsRequired, *fields), max, min, required)

private fun array(key: String, item: A2uiSchema, max: Int, min: Int = 0, required: Boolean = true) =
    A2uiProperty.dynamicCustom(
        key = key,
        schema = A2uiAnySchema(
            keywords = listOf(A2uiSchemaKeyword.OneOf(listOf(
                A2uiArraySchema(items = item, minItems = min, maxItems = max),
                A2uiDataBindingSchema.DEFAULT_INSTANCE,
                A2uiFunctionCallSchema.DEFAULT_INSTANCE,
            ))),
        ),
        // 数据模型绑定可绕过字面量约束；渲染端同样限制列表规模。
        safeCast = { (it as? List<*>)?.takeIf { list -> list.size in min..max } },
        required = required,
    )

private fun component(
    name: String,
    description: String,
    properties: List<A2uiProperty<*>>,
    content: @Composable (CardValues, Modifier) -> Unit,
): A2uiComponent = CatalogCard(name, description, properties, content)

private class CatalogCard(
    override val name: String,
    override val description: String,
    override val properties: List<A2uiProperty<*>>,
    private val content: @Composable (CardValues, Modifier) -> Unit,
) : A2uiComponent {
    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        this@CatalogCard.properties.filterIsInstance<DynamicA2uiProperty<*>>()
            .all { !it.isRequired || readValue(properties, it) != null }

    // 目录只注册非空值类型；类型检查由属性自己的 safeCast 执行。
    @Composable
    @Suppress("UNCHECKED_CAST")
    private fun A2uiComponentScope.readValue(
        properties: A2uiComponentProperties,
        property: DynamicA2uiProperty<*>,
    ): Any? = properties.bind(property as DynamicA2uiProperty<Any>)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val values = this@CatalogCard.properties.associate { property ->
            property.key to when (property) {
                is DynamicA2uiProperty<*> -> readValue(properties, property)
                is StaticA2uiProperty<*> -> properties[property]
                else -> null
            }
        }
        content(CardValues(values), modifier)
    }
}

/** 只读卡片字段，保留原始数值用于排序、汇总及绘图。 */
internal class CardValues(private val values: Map<*, *>) {
    fun text(key: String): String = values[key] as? String ?: ""
    fun number(key: String): Double? = (values[key] as? Number)?.toDouble()?.takeIf(Double::isFinite)
    fun flag(key: String): Boolean = values[key] == true
    fun list(key: String, max: Int = 300): List<*> = (values[key] as? List<*>)?.take(max).orEmpty()
    fun rows(key: String, max: Int = 100): List<CardValues> = list(key, max).mapNotNull {
        (it as? Map<*, *>)?.let(::CardValues)
    }
    fun cell(key: String): Any? = values[key].takeIf { it is Number || it is String || it is Boolean }
}

private object ChatSwitch : A2uiComponent {
    override val name = "Switch"
    override val description = "An on/off setting with a label and optional description. Bind value to a writable data-model path for interaction; use CheckBox for form confirmations."
    private val label = label("label", true)
    private val details = label("description")
    private val value = A2uiProperty.dynamicBoolean(key = "value")
    override val properties = listOf(label, details, value)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(label) != null && (value !in properties || properties.bind(value) != null)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        SettingSwitch(
            label = properties.bind(label).orEmpty(),
            description = properties.bind(details).orEmpty(),
            checked = properties.bind(value) ?: false,
            update = properties.bindUpdater(value),
            modifier = modifier,
        )
    }
}

private object ChatChipSelect : A2uiComponent {
    override val name = "ChipSelect"
    override val description = "Multi-select chips bound to a string-list path: topics, filters, preferences."
    private val label = label("label")
    private val value = A2uiProperty.dynamicCustom<List<String>>(
        key = "value",
        schema = A2uiAnySchema(
            keywords = listOf(A2uiSchemaKeyword.OneOf(listOf(
                A2uiArraySchema(items = A2uiStringSchema.INSTANCE),
                A2uiDataBindingSchema.DEFAULT_INSTANCE,
                A2uiFunctionCallSchema.DEFAULT_INSTANCE,
            ))),
        ),
        safeCast = { raw -> (raw as? List<*>)?.mapNotNull { it as? String } },
    )
    private val options = rows("options", 30, "value label", "value" to string, "label" to string, min = 1)
    override val properties = listOf(label, value, options)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(options) != null

    @Composable
    @Suppress("UNCHECKED_CAST")
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val optionRows = (properties.bind(options) as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.let(::CardValues) }
            .orEmpty()
        ChipSelect(
            label = properties.bind(label).orEmpty(),
            selectedValues = properties.bind(value).orEmpty(),
            options = optionRows.map { it.text("value") to it.text("label") }.filter { it.first.isNotBlank() },
            update = properties.bindUpdater(value),
            modifier = modifier,
        )
    }
}
