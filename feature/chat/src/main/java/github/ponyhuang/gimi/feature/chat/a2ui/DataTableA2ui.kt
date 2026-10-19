package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R

/** 聊天目录的原生只读表格，不包含业务操作或编辑入口。 */
internal object ChatA2uiDataTable : A2uiComponent {
    override val name = "DataTable"
    override val description = "Native read-only data table for comparisons, metrics and structured query results. " +
        "Use for tabular information instead of recreating aligned rows with Text/Row/List. " +
        "Use specialized weather, news and music components when they match the request. " +
        "Supports a fixed header, horizontal scrolling for wide tables and internal vertical scrolling."
    override val properties = listOf(DataTableA2uiProperty)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties): Boolean =
        properties.bind(DataTableA2uiProperty) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val data = properties.bind(DataTableA2uiProperty) ?: return
        DataTableCard(data, modifier)
    }
}

@Composable
internal fun DataTableCard(data: DataTableData, modifier: Modifier = Modifier) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                data.heading ?: stringResource(R.string.chat_table_default_heading),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.chat_table_row_count, data.rows.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 所有行共享列宽与横向滚动，表体单独纵向滚动，保证表头不会漂移或错列。
            val minimumWidths = data.columns.map { if (it.alignEnd) 76.dp else 120.dp }
            val minimumWidth = minimumWidths.fold(0.dp) { sum, width -> sum + width } + 28.dp
            val tableWidth = maxOf(maxWidth, minimumWidth)
            val extraWidth = (tableWidth - minimumWidth) / data.columns.size
            val widths = minimumWidths.map { it + extraWidth }
            Column(
                Modifier.horizontalScroll(horizontalScroll).width(tableWidth).semantics {
                    collectionInfo = CollectionInfo(rowCount = data.rows.size + 1, columnCount = data.columns.size)
                },
            ) {
                DataTableRow(data.columns.map { it.label }, data.columns, widths, 0, header = true)
                Column(Modifier.heightIn(max = 192.dp).verticalScroll(verticalScroll)) {
                    data.rows.forEachIndexed { index, cells ->
                        DataTableRow(cells, data.columns, widths, index + 1, header = false)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    }
                }
            }
        }
        if (data.rows.isEmpty()) {
            Text(
                stringResource(R.string.chat_table_empty),
                Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (horizontalScroll.maxValue > 0 || verticalScroll.maxValue > 0) {
            val hint = when {
                horizontalScroll.maxValue > 0 && verticalScroll.maxValue > 0 -> R.string.chat_table_scroll_both
                horizontalScroll.maxValue > 0 -> R.string.chat_table_scroll_horizontal
                else -> R.string.chat_table_scroll_vertical
            }
            Text(
                stringResource(hint), Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataTableRow(
    cells: List<String>,
    columns: List<DataTableColumn>,
    widths: List<Dp>,
    rowIndex: Int,
    header: Boolean,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
            .padding(horizontal = 14.dp),
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                modifier = Modifier.width(widths[index]).semantics {
                    collectionItemInfo = CollectionItemInfo(rowIndex, 1, index, 1)
                    if (header) heading()
                }.padding(horizontal = 4.dp, vertical = 12.dp),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (columns[index].alignEnd) TextAlign.End else TextAlign.Start,
            )
        }
    }
}
