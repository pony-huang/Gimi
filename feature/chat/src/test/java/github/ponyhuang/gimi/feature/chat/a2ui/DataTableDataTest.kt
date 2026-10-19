package github.ponyhuang.gimi.feature.chat.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 锁定不可信表格数据的列对齐边界，避免坏数据被静默丢弃后显示在错误表头下。 */
class DataTableDataTest {
    @Test
    fun preservesFormattedCellsColumnOrderAndAlignment() {
        val data = requireNotNull(DataTableData.fromA2ui(payload(listOf(listOf("A", "¥ 1.20")))))
        assertEquals(listOf(DataTableColumn("名称"), DataTableColumn("金额", true)), data.columns)
        assertEquals(listOf(listOf("A", "¥ 1.20")), data.rows)
        assertEquals("数据", data.heading)
    }

    @Test
    fun acceptsEmptyResultsAndRejectsAbsentRows() {
        assertEquals(emptyList<List<String>>(), requireNotNull(DataTableData.fromA2ui(payload(emptyList<Any>()))).rows)
        assertNull(DataTableData.fromA2ui(payload(emptyList<Any>()) - "rows"))
    }

    @Test
    fun rejectsMalformedOrMisalignedRowsRatherThanShiftingCells() {
        for (rows in listOf(
            listOf(listOf("missing cell")),
            listOf(listOf("A", "1", "extra cell")),
            listOf(listOf("A", 1)),
            listOf(listOf("A", null)),
            listOf("not a row"),
        )) {
            assertNull(DataTableData.fromA2ui(payload(rows)))
        }
    }

    @Test
    fun rejectsInvalidColumnsAndOversizedTables() {
        val valid = payload(listOf(listOf("A", "1")))
        for (columns in listOf(
            emptyList(),
            listOf(mapOf("label" to " ")),
            listOf(mapOf("label" to "A", "align" to "invalid")),
            List(DataTableData.MAX_COLUMNS + 1) { mapOf("label" to "A") },
        )) {
            assertNull(DataTableData.fromA2ui(valid + ("columns" to columns)))
        }
        assertNotNull(DataTableData.fromA2ui(payload(List(DataTableData.MAX_ROWS) { listOf("A", "1") })))
        assertNull(DataTableData.fromA2ui(payload(List(DataTableData.MAX_ROWS + 1) { listOf("A", "1") })))
    }

    private fun payload(rows: List<*>): Map<String, Any> = mapOf(
        "heading" to "数据",
        "columns" to listOf(mapOf("label" to "名称"), mapOf("label" to "金额", "align" to "end")),
        "rows" to rows,
    )
}
