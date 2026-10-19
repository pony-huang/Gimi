package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.model.schema.A2uiAnySchema
import androidx.a2ui.model.schema.A2uiArraySchema
import androidx.a2ui.model.schema.A2uiObjectSchema
import androidx.a2ui.model.schema.A2uiSchemaKeyword
import androidx.a2ui.model.schema.A2uiStringSchema
import androidx.a2ui.model.schema.commontypes.A2uiDataBindingSchema
import androidx.a2ui.model.schema.commontypes.A2uiFunctionCallSchema

/** 表格列；[label] 为本地化表头，[alignEnd] 表示数值或金额等内容靠末端对齐。 */
internal data class DataTableColumn(val label: String, val alignEnd: Boolean = false)

/**
 * 只读表格的展示快照，不承担排序、计算或业务操作。
 *
 * @property heading 可选标题，省略时使用本地化默认标题。
 * @property columns 有序列定义，最多八列。
 * @property rows 与列位置一一对应的已格式化单元格，最多一百行；空列表展示空状态。
 */
internal data class DataTableData(
    val heading: String?,
    val columns: List<DataTableColumn>,
    val rows: List<List<String>>,
) {
    companion object {
        const val MAX_COLUMNS = 8
        const val MAX_ROWS = 100

        fun fromA2ui(value: Any): DataTableData? {
            if (value is DataTableData) return value
            val map = value as? Map<*, *> ?: return null
            val rawColumns = map["columns"] as? List<*> ?: return null
            if (rawColumns.size !in 1..MAX_COLUMNS) return null
            val columns = rawColumns.map { entry ->
                val column = entry as? Map<*, *> ?: return null
                val label = (column["label"] as? String)?.takeIf { it.isNotBlank() } ?: return null
                val align = column["align"] ?: "start"
                if (align != "start" && align != "end") return null
                DataTableColumn(label, alignEnd = align == "end")
            }
            val rawRows = map["rows"] as? List<*> ?: return null
            if (rawRows.size > MAX_ROWS) return null
            // 绑定数据也必须遵守列数契约，不能丢弃坏单元格后将其余数据错列展示。
            val rows = rawRows.map { entry ->
                val row = entry as? List<*> ?: return null
                if (row.size != columns.size) return null
                row.map { it as? String ?: return null }
            }
            val heading = map["heading"]
            if (heading != null && heading !is String) return null
            return DataTableData((heading as? String)?.takeIf { it.isNotBlank() }, columns, rows)
        }
    }
}

private val dataTableSchema = A2uiObjectSchema(
    description = "Read-only table. Each row must contain exactly one formatted string per column, in column order.",
    properties = mapOf(
        "heading" to A2uiStringSchema("Optional localized heading."),
        "columns" to A2uiArraySchema(
            items = A2uiObjectSchema(
                properties = mapOf(
                    "label" to A2uiStringSchema("Localized column heading; must not be blank."),
                    "align" to A2uiStringSchema(
                        "Use end for numbers/amounts and start for text. Defaults to start.",
                        keywords = listOf(A2uiSchemaKeyword.Enum(listOf("start", "end"))),
                    ),
                ),
                required = setOf("label"),
                isAdditionalPropertiesAllowed = false,
            ),
            minItems = 1,
            maxItems = DataTableData.MAX_COLUMNS,
        ),
        "rows" to A2uiArraySchema(
            description = "Up to 100 rows, each with exactly columns.size strings. Use [] for an empty table. " +
                "Format numbers, dates, units and missing values in the user's language before sending.",
            items = A2uiArraySchema(
                items = A2uiStringSchema.INSTANCE,
                minItems = 1,
                maxItems = DataTableData.MAX_COLUMNS,
            ),
            maxItems = DataTableData.MAX_ROWS,
        ),
    ),
    required = setOf("columns", "rows"),
    isAdditionalPropertiesAllowed = false,
)

/** 表格整体动态绑定，复用宿主的流式数据模型更新与函数求值。 */
internal val DataTableA2uiProperty = A2uiProperty.dynamicCustom(
    key = "table",
    schema = A2uiAnySchema(
        description = "Table object or a dynamic binding such as {\"path\":\"/table\"}.",
        keywords = listOf(A2uiSchemaKeyword.OneOf(listOf(
            dataTableSchema,
            A2uiDataBindingSchema.DEFAULT_INSTANCE,
            A2uiFunctionCallSchema.DEFAULT_INSTANCE,
        ))),
    ),
    safeCast = { DataTableData.fromA2ui(it) },
    required = true,
)
