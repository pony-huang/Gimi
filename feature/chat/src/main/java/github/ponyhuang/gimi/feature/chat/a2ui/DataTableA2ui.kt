package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import github.ponyhuang.gimi.core.storage.AndroidAppDirectoryResolver
import github.ponyhuang.gimi.core.storage.ShareableFileUriFactory
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.feature.chat.chatShareableDirectorySpec
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 表体视口最多展示的行数，超出的行通过表体纵向滚动查看；全屏查看时不设上限。 */
private const val MAX_VISIBLE_ROWS = 10

/** 单元格垂直内边距，需与 [DataTableRow] 的 padding 保持一致。 */
private val CellVerticalPadding = 12.dp

/** 行/列分隔线厚度，需与 [HorizontalDivider]、[VerticalDivider] 的厚度保持一致。 */
private val DividerThickness = 1.dp

/** 行、列分隔线的统一弱化色。 */
@Composable
private fun dataTableDividerColor(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

/** 聊天目录的原生只读表格，不包含业务操作或编辑入口。 */
internal object ChatA2uiDataTable : A2uiComponent {
    override val name = "DataTable"
    override val description = "Native read-only data table for comparisons, metrics and structured query results. " +
        "Use for tabular information instead of recreating aligned rows with Text/Row/List. " +
        "Use specialized weather and music components when they match the request. " +
        "Supports a fixed header, horizontal scrolling for wide tables and internal vertical scrolling. " +
        "The body viewport shows at most ten rows at a time; longer tables scroll vertically inside the card. " +
        "Use exactly one DataTable as the card root; do not add a Text title above it or a summary/footer " +
        "Text below it — the component renders its own heading and totals belong inside the table. " +
        "The client renders copy, image export, fullscreen preview and share actions on the card automatically; " +
        "do not recreate them with buttons."
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
    var expanded by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var justCopied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    val copiedMessage = stringResource(R.string.chat_message_copied)
    val exportFailedMessage = stringResource(R.string.chat_table_export_failed)
    val shareLabel = stringResource(R.string.chat_table_share)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                // 每帧把卡片内容录制进离屏图层，供「保存为图片」导出当前完整卡片。
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                data.heading ?: stringResource(R.string.chat_table_default_heading),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DataTableHeaderAction(
                icon = if (justCopied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                label = stringResource(R.string.chat_table_copy),
                tint = if (justCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("data table", data.toPlainText())),
                    )
                    // 复制成功后的短暂 ✓ 反馈：动作有始有终，不只依赖 Toast（部分 ROM 会吞掉）。
                    justCopied = true
                    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    delay(1_600.milliseconds)
                    justCopied = false
                }
            }
            DataTableHeaderAction(
                icon = Icons.Outlined.Image,
                label = stringResource(R.string.chat_table_export_image),
                enabled = !exporting,
            ) {
                exporting = true
                scope.launch {
                    val exported = runCatching {
                        exportDataTableCard(context, graphicsLayer, data, shareLabel)
                    }.isSuccess
                    exporting = false
                    if (!exported) Toast.makeText(context, exportFailedMessage, Toast.LENGTH_SHORT).show()
                }
            }
            DataTableHeaderAction(
                icon = Icons.Outlined.OpenInFull,
                label = stringResource(R.string.chat_table_expand),
            ) {
                expanded = true
            }
            DataTableHeaderAction(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.chat_table_share),
            ) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, data.toPlainText())
                    data.heading?.let { putExtra(Intent.EXTRA_TITLE, it) }
                }
                runCatching { context.startActivity(Intent.createChooser(send, shareLabel)) }
            }
        }
        DataTableBody(data, Modifier.fillMaxWidth(), maxVisibleRows = MAX_VISIBLE_ROWS)
        if (data.rows.isEmpty()) {
            Text(
                stringResource(R.string.chat_table_empty),
                Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (expanded) {
        DataTableFullscreenDialog(data, onDismiss = { expanded = false })
    }
}

/** 卡片头部右侧的图标操作按钮。 */
@Composable
private fun DataTableHeaderAction(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp), enabled = enabled) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** 全屏查看：不设行数视口上限，完整浏览表格后关闭。 */
@Composable
private fun DataTableFullscreenDialog(data: DataTableData, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    data.heading ?: stringResource(R.string.chat_table_default_heading),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.chat_table_close))
                }
            }
            DataTableBody(data, Modifier.weight(1f).fillMaxWidth(), maxVisibleRows = null)
        }
    }
}

@Composable
private fun DataTableBody(data: DataTableData, modifier: Modifier = Modifier, maxVisibleRows: Int?) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    // 行高随字体缩放，大字号下不会被过早截断。
    val rowHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyMedium.lineHeight.toDp()
    } + CellVerticalPadding * 2
    val headerRowHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    } + CellVerticalPadding * 2 + DividerThickness

    BoxWithConstraints(modifier) {
        val availableViewport = maxVisibleRows?.let { (rowHeight + DividerThickness) * it }
            ?: (maxHeight - headerRowHeight).coerceAtLeast(rowHeight)
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
            HorizontalDivider(color = dataTableDividerColor())
            Column(
                Modifier.heightIn(max = availableViewport).verticalScroll(verticalScroll),
            ) {
                data.rows.forEachIndexed { index, cells ->
                    DataTableRow(cells, data.columns, widths, index + 1, header = false)
                    if (index < data.rows.lastIndex) {
                        HorizontalDivider(color = dataTableDividerColor())
                    }
                }
            }
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
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 14.dp),
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = DividerThickness,
                    color = dataTableDividerColor(),
                )
            }
            Text(
                text = cell,
                modifier = Modifier.width(widths[index]).semantics {
                    collectionItemInfo = CollectionItemInfo(rowIndex, 1, index, 1)
                    if (header) heading()
                }.padding(horizontal = 4.dp, vertical = CellVerticalPadding),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (columns[index].alignEnd) TextAlign.End else TextAlign.Start,
            )
        }
    }
}

/** 表格导出为 PNG 后经系统分享面板发送；文件写入受管的聊天共享缓存目录。 */
private suspend fun exportDataTableCard(
    context: Context,
    graphicsLayer: GraphicsLayer,
    data: DataTableData,
    shareLabel: String,
) {
    val bitmap = graphicsLayer.toImageBitmap()
    val shareableRoot = AndroidAppDirectoryResolver(context).resolve(
        chatShareableDirectorySpec,
        create = true,
    )
    val directory = File(shareableRoot, "tables").apply {
        check(exists() || mkdirs()) { "Could not create table export cache directory" }
    }
    val file = File.createTempFile("table_", ".png", directory)
    file.outputStream().use { stream ->
        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    val uri = ShareableFileUriFactory.uriFor(context, file, chatShareableDirectorySpec)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        data.heading?.let { putExtra(Intent.EXTRA_TITLE, it) }
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, shareLabel))
}

/** 复制/分享用纯文本：表头 + 行以制表符分列、换行分行，可直接粘贴进表格软件。 */
internal fun DataTableData.toPlainText(): String = buildString {
    append(columns.joinToString("\t") { it.label })
    rows.forEach { row ->
        append('\n')
        append(row.joinToString("\t"))
    }
}
