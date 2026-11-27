package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.LocalGimiDarkTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private val chartColors = listOf(
    Color(0xFF8B5CF6),
    Color(0xFF38BDF8),
    Color(0xFF34D399),
    Color(0xFFF59E0B),
    Color(0xFFF472B6),
)

private val CandlePositiveDark = Color(0xFF34D399)
private val CandlePositiveLight = Color(0xFF059669)
private val CandleNegativeDark = Color(0xFFF87171)
private val CandleNegativeLight = Color(0xFFE11D48)

private data class BarDatum(val x: String, val series: String, val y: Double)

private data class CandleDatum(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
) {
    val isUp: Boolean get() = close >= open
}

@Composable
private fun ChartCardFrame(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    empty: Boolean = false,
    aside: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title.isNotBlank() || subtitle.isNotBlank() || aside != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = if (aside != null) 12.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (title.isNotBlank()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.onSurface,
                            )
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    aside?.invoke()
                }
            }

            if (empty) {
                Text(
                    text = stringResource(R.string.chat_a2ui_chart_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BarChart(values: CardValues, modifier: Modifier) {
    val format = values.text("format")
    val unit = values.text("unit")
    val stacked = values.flag("stacked")
    val horizontal = values.flag("horizontal")
    val highlight = values.text("highlight")
    val rawRows = values.rows("data").mapNotNull { row ->
        val x = row.cell("x")?.toString()?.takeIf { it.isNotBlank() } ?: row.text("x")
        val y = row.number("y") ?: return@mapNotNull null
        BarDatum(x = x, series = row.text("series"), y = y)
    }
    val categories = remember(rawRows) { rawRows.map { it.x }.distinct() }
    val groupedByCat = remember(rawRows) { rawRows.groupBy { it.x } }
    val seriesNames = remember(rawRows) { rawRows.map { it.series }.distinct() }
    val multi = seriesNames.size > 1
    val palette = remember(seriesNames) {
        seriesNames.mapIndexed { idx, name -> name to chartColors[idx % chartColors.size] }.toMap()
    }

    var selectedIndex by remember(rawRows) { mutableStateOf<Int?>(null) }

    val minVal = remember(groupedByCat, stacked) {
        if (stacked) {
            groupedByCat.values.minOfOrNull { g -> g.filter { it.y < 0 }.sumOf { it.y } } ?: 0.0
        } else {
            rawRows.minOfOrNull { it.y } ?: 0.0
        }.coerceAtMost(0.0)
    }
    val maxVal = remember(groupedByCat, stacked) {
        if (stacked) {
            groupedByCat.values.maxOfOrNull { g -> g.filter { it.y >= 0 }.sumOf { it.y } } ?: 1.0
        } else {
            rawRows.maxOfOrNull { it.y } ?: 1.0
        }.coerceAtLeast(0.0)
    }
    val ticks = remember(minVal, maxVal) { computeNiceTicks(minVal, maxVal, desiredCount = 5) }
    val axisMin = ticks.firstOrNull() ?: 0.0
    val axisMax = ticks.lastOrNull()?.takeIf { it > axisMin } ?: 1.0
    val axisSpan = axisMax - axisMin

    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    val axisLabelStyle = TextStyle(
        fontSize = 10.5.sp,
        color = colors.onSurfaceVariant,
        fontFeatureSettings = "tnum",
    )
    val gridColor = colors.outlineVariant.copy(alpha = 0.55f)
    val chartHeight = (values.number("height")?.toInt() ?: if (horizontal) {
        max(160, categories.size * (if (multi && !stacked) 38 else 28) + 32)
    } else 210).coerceIn(120, 420).dp

    val a11yDesc = categories.joinToString("; ") { cat ->
        val items = groupedByCat[cat].orEmpty()
        "$cat: " + items.joinToString(", ") { item ->
            val v = formatChartValue(item.y, format, unit)
            if (item.series.isNotBlank()) "${item.series} $v" else v
        }
    }

    ChartCardFrame(
        title = values.text("title"),
        subtitle = values.text("subtitle"),
        modifier = modifier,
        empty = categories.isEmpty(),
        aside = if (multi) {
            {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    seriesNames.filter { it.isNotBlank() }.forEach { name ->
                        val active = highlight.isBlank() || highlight == name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        palette.getValue(name).copy(alpha = if (active) 1f else 0.32f),
                                        CircleShape,
                                    ),
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant.copy(alpha = if (active) 1f else 0.45f),
                            )
                        }
                    }
                }
            }
        } else null,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = a11yDesc }
                .pointerInput(categories, horizontal) {
                    detectTapGestures { offset ->
                        if (categories.isEmpty()) return@detectTapGestures
                        val idx = if (horizontal) {
                            val plotTop = 6.dp.toPx()
                            val plotBottom = size.height - 24.dp.toPx()
                            if (plotBottom <= plotTop) 0
                            else (((offset.y - plotTop) / (plotBottom - plotTop)) * categories.size).toInt()
                        } else {
                            val plotLeft = 46.dp.toPx()
                            val plotRight = size.width - 4.dp.toPx()
                            if (plotRight <= plotLeft) 0
                            else (((offset.x - plotLeft) / (plotRight - plotLeft)) * categories.size).toInt()
                        }.coerceIn(0, categories.lastIndex)
                        selectedIndex = if (selectedIndex == idx) null else idx
                    }
                },
        ) {
            if (size.width <= 0f || size.height <= 0f || categories.isEmpty()) return@Canvas
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)

            if (horizontal) {
                val leftPad = 68.dp.toPx()
                val bottomPad = 24.dp.toPx()
                val topPad = 4.dp.toPx()
                val plotW = (size.width - leftPad - 6.dp.toPx()).coerceAtLeast(1f)
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                val zeroX = leftPad + (((0.0 - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f) * plotW)

                ticks.forEach { tick ->
                    val frac = ((tick - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f)
                    val x = leftPad + frac * plotW
                    drawLine(gridColor, Offset(x, topPad), Offset(x, topPad + plotH), strokeWidth = 1.dp.toPx(), pathEffect = dashEffect)
                    val label = formatAxisTick(tick, format, unit)
                    val measured = textMeasurer.measure(label, axisLabelStyle)
                    drawText(
                        measured,
                        topLeft = Offset((x - measured.size.width / 2f).coerceIn(leftPad, size.width - measured.size.width), topPad + plotH + 6.dp.toPx()),
                    )
                }

                val slotH = plotH / categories.size
                categories.forEachIndexed { catIdx, cat ->
                    val group = groupedByCat[cat].orEmpty()
                    val catTop = topPad + catIdx * slotH
                    val measuredCat = textMeasurer.measure(cat, axisLabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = androidx.compose.ui.unit.Constraints(maxWidth = (leftPad - 8.dp.toPx()).toInt().coerceAtLeast(1)))
                    drawText(measuredCat, topLeft = Offset((leftPad - measuredCat.size.width - 8.dp.toPx()).coerceAtLeast(0f), catTop + (slotH - measuredCat.size.height) / 2f))

                    val innerH = slotH * 0.70f
                    val barStartY = catTop + (slotH - innerH) / 2f
                    val barCount = if (stacked || !multi) 1 else group.size.coerceAtLeast(1)
                    val barH = (innerH / barCount).coerceAtMost(22.dp.toPx())
                    val totalBarsH = barH * barCount
                    val offsetCenterY = barStartY + (innerH - totalBarsH) / 2f

                    var posCursor = zeroX
                    var negCursor = zeroX
                    group.forEachIndexed { sIdx, item ->
                        val deltaPx = ((item.y / axisSpan).toFloat() * plotW)
                        val startX = if (!stacked) zeroX else if (item.y >= 0) posCursor else negCursor
                        if (stacked) {
                            if (item.y >= 0) posCursor += deltaPx else negCursor += deltaPx
                        }
                        val left = min(startX, startX + deltaPx)
                        val width = abs(deltaPx).coerceAtLeast(2.dp.toPx())
                        val y = if (stacked || !multi) offsetCenterY else offsetCenterY + sIdx * barH
                        val dimmed = (selectedIndex != null && selectedIndex != catIdx) || (highlight.isNotBlank() && highlight != item.series)
                        val barColor = palette.getValue(item.series).copy(alpha = if (dimmed) 0.32f else 1f)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(left, y + 1.dp.toPx()),
                            size = Size(width, (barH - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            } else {
                val leftPad = 48.dp.toPx()
                val bottomPad = 26.dp.toPx()
                val topPad = 8.dp.toPx()
                val plotW = (size.width - leftPad - 4.dp.toPx()).coerceAtLeast(1f)
                val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                val zeroY = topPad + plotH - (((0.0 - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f) * plotH)

                ticks.forEach { tick ->
                    val frac = ((tick - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f)
                    val y = topPad + plotH - frac * plotH
                    drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1.dp.toPx(), pathEffect = if (tick == 0.0) null else dashEffect)
                    val label = formatAxisTick(tick, format, unit)
                    val measured = textMeasurer.measure(label, axisLabelStyle)
                    drawText(
                        measured,
                        topLeft = Offset((leftPad - measured.size.width - 8.dp.toPx()).coerceAtLeast(0f), y - measured.size.height / 2f),
                    )
                }

                val slotW = plotW / categories.size
                categories.forEachIndexed { catIdx, cat ->
                    val group = groupedByCat[cat].orEmpty()
                    val catLeft = leftPad + catIdx * slotW
                    val measuredCat = textMeasurer.measure(
                        cat,
                        axisLabelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = (slotW - 4.dp.toPx()).toInt().coerceAtLeast(1)),
                    )
                    drawText(
                        measuredCat,
                        topLeft = Offset(catLeft + (slotW - measuredCat.size.width) / 2f, topPad + plotH + 6.dp.toPx()),
                    )

                    val groupW = slotW * (if (multi) 0.74f else 0.62f)
                    val barCount = if (stacked || !multi) 1 else group.size.coerceAtLeast(1)
                    val maxBarW = if (stacked || !multi) 40.dp.toPx() else 18.dp.toPx()
                    val barW = ((groupW - (barCount - 1) * 3.dp.toPx()) / barCount).coerceIn(4.dp.toPx(), maxBarW)
                    val totalBarsW = barW * barCount + (barCount - 1) * 3.dp.toPx()
                    val startGroupX = catLeft + (slotW - totalBarsW) / 2f

                    var posCursor = zeroY
                    var negCursor = zeroY
                    group.forEachIndexed { sIdx, item ->
                        val deltaPx = ((item.y / axisSpan).toFloat() * plotH)
                        val base = if (!stacked) zeroY else if (item.y >= 0) posCursor else negCursor
                        if (stacked) {
                            if (item.y >= 0) posCursor -= deltaPx else negCursor -= deltaPx
                        }
                        val top = min(base, base - deltaPx)
                        val height = abs(deltaPx).coerceAtLeast(2.dp.toPx())
                        val x = if (stacked || !multi) startGroupX else startGroupX + sIdx * (barW + 3.dp.toPx())
                        val dimmed = (selectedIndex != null && selectedIndex != catIdx) || (highlight.isNotBlank() && highlight != item.series)
                        val barColor = palette.getValue(item.series).copy(alpha = if (dimmed) 0.32f else 1f)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, top),
                            size = Size(barW, height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                }
            }
        }

        selectedIndex?.let { idx ->
            val cat = categories.getOrNull(idx) ?: return@let
            val items = groupedByCat[cat].orEmpty()
            ChartSelectionBanner(
                title = cat,
                items = items.map { item ->
                    Triple(
                        palette.getValue(item.series),
                        item.series.ifBlank { values.text("title") },
                        formatChartValue(item.y, format, unit),
                    )
                },
            )
        }
    }
}

@Composable
internal fun PieChart(values: CardValues, modifier: Modifier) {
    val segments = values.rows("data").mapNotNull { row ->
        row.number("value")?.takeIf { it >= 0 }?.let { row.text("label") to it }
    }
    val total = segments.sumOf { it.second }
    // 默认使用环形图，与 a2ui.open.bot 的 donut !== false 保持一致
    val donut = values.cell("donut") != false
    val format = values.text("format")
    val unit = values.text("unit")
    var selected by remember(segments) { mutableStateOf<Int?>(null) }

    val colors = MaterialTheme.colorScheme
    val chartSize = (values.number("height")?.toInt() ?: 152).coerceIn(110, 220).dp

    ChartCardFrame(
        title = values.text("title"),
        subtitle = values.text("subtitle"),
        modifier = modifier,
        empty = segments.isEmpty() || total <= 0.0,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(chartSize),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(segments) {
                            detectTapGestures { offset ->
                                if (segments.isEmpty() || total <= 0.0) return@detectTapGestures
                                val dx = offset.x - size.width / 2f
                                val dy = offset.y - size.height / 2f
                                val deg = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0)
                                var acc = 0.0
                                val hit = segments.indexOfFirst { (_, v) ->
                                    val sweep = 360.0 * (v / total)
                                    val inside = deg >= acc && deg < acc + sweep
                                    acc += sweep
                                    inside
                                }
                                if (hit >= 0) {
                                    selected = if (selected == hit) null else hit
                                }
                            }
                        },
                ) {
                    if (size.minDimension <= 0f || total <= 0.0) return@Canvas
                    val gapDeg = if (segments.size > 1) 2.2f else 0f
                    var start = -90f
                    val diameter = size.minDimension
                    val ringWidth = diameter * 0.24f
                    val inset = if (donut) ringWidth / 2f else 0f
                    val bounds = Size(diameter - inset * 2f, diameter - inset * 2f)

                    segments.forEachIndexed { index, (_, value) ->
                        val rawSweep = (360.0 * (value / total)).toFloat()
                        val sweep = (rawSweep - gapDeg).coerceAtLeast(1f)
                        val color = chartColors[index % chartColors.size]
                            .copy(alpha = if (selected == null || selected == index) 1f else 0.35f)
                        if (donut) {
                            drawArc(
                                color = color,
                                startAngle = start + gapDeg / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = bounds,
                                style = Stroke(width = ringWidth, cap = StrokeCap.Butt),
                            )
                        } else {
                            drawArc(
                                color = color,
                                startAngle = start + gapDeg / 2f,
                                sweepAngle = sweep,
                                useCenter = true,
                            )
                        }
                        start += rawSweep
                    }
                }

                if (donut) {
                    val activeValue = selected?.let { segments[it].second } ?: total
                    val activeLabel = selected?.let { segments[it].first }
                        ?: values.text("centerLabel").ifBlank { stringResource(R.string.chat_a2ui_total) }
                    val centerFmt = when (format) {
                        "currency" -> "currency"
                        "percent" -> "percent"
                        else -> "compact"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = formatChartValue(activeValue, centerFmt, unit),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = activeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                segments.forEachIndexed { index, (label, value) ->
                    val share = value / total
                    val sharePct = if (share < 0.1) {
                        String.format(Locale.US, "%.1f%%", share * 100.0)
                    } else {
                        String.format(Locale.US, "%.0f%%", share * 100.0)
                    }
                    val rowFmt = if (format == "currency") "currency" else "compact"
                    val isDimmed = selected != null && selected != index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected == index) {
                                selected = if (selected == index) null else index
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    chartColors[index % chartColors.size].copy(alpha = if (isDimmed) 0.35f else 1f),
                                    RoundedCornerShape(3.dp),
                                ),
                        )
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant.copy(alpha = if (isDimmed) 0.5f else 1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatChartValue(value, rowFmt, if (rowFmt == "currency") "" else unit),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.onSurface.copy(alpha = if (isDimmed) 0.5f else 1f),
                        )
                        Text(
                            text = sharePct,
                            modifier = Modifier.width(38.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CandlestickChart(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val format = values.text("format")
    val unit = values.text("unit")
    val candles = remember(values) {
        values.rows("data").mapNotNull { row ->
            val date = row.text("date").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val open = row.number("open") ?: return@mapNotNull null
            val high = row.number("high") ?: return@mapNotNull null
            val low = row.number("low") ?: return@mapNotNull null
            val close = row.number("close") ?: return@mapNotNull null
            CandleDatum(
                date = date,
                open = open,
                high = maxOf(high, open, close),
                low = minOf(low, open, close),
                close = close,
            )
        }
    }

    val positiveColor = if (darkTheme) CandlePositiveDark else CandlePositiveLight
    val negativeColor = if (darkTheme) CandleNegativeDark else CandleNegativeLight
    val firstOpen = candles.firstOrNull()?.open
    val lastCandle = candles.lastOrNull()
    val periodChangePct = if (firstOpen != null && firstOpen != 0.0 && lastCandle != null) {
        ((lastCandle.close - firstOpen) / firstOpen) * 100.0
    } else 0.0

    var selectedIndex by remember(candles) { mutableStateOf<Int?>(null) }

    val minLow = candles.minOfOrNull { it.low } ?: 0.0
    val maxHigh = candles.maxOfOrNull { it.high } ?: 1.0
    val ticks = remember(minLow, maxHigh) { computeNiceTicks(minLow, maxHigh, desiredCount = 5) }
    val axisMin = ticks.firstOrNull() ?: minLow
    val axisMax = ticks.lastOrNull()?.takeIf { it > axisMin } ?: (axisMin + 1.0)
    val axisSpan = axisMax - axisMin
    val minBody = ((maxHigh - minLow).takeIf { it > 0 } ?: 1.0) * 0.006

    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = TextStyle(
        fontSize = 10.5.sp,
        color = colors.onSurfaceVariant,
        fontFeatureSettings = "tnum",
    )
    val gridColor = colors.outlineVariant.copy(alpha = 0.55f)
    val chartHeight = (values.number("height")?.toInt() ?: 220).coerceIn(140, 380).dp

    ChartCardFrame(
        title = values.text("title"),
        subtitle = values.text("subtitle"),
        modifier = modifier,
        empty = candles.isEmpty(),
        aside = lastCandle?.let { last ->
            {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatChartValue(last.close, format, unit),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                        color = colors.onSurface,
                    )
                    val sign = if (periodChangePct >= 0) "+" else ""
                    val changeColor = if (periodChangePct >= 0) positiveColor else negativeColor
                    Text(
                        text = "$sign${String.format(Locale.US, "%.2f", periodChangePct)}% ${stringResource(R.string.chat_a2ui_over_period)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = changeColor,
                    )
                }
            }
        },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(candles) {
                    detectTapGestures { offset ->
                        if (candles.isEmpty()) return@detectTapGestures
                        val leftPad = 52.dp.toPx()
                        val plotW = (size.width - leftPad - 6.dp.toPx()).coerceAtLeast(1f)
                        val idx = (((offset.x - leftPad) / plotW) * candles.size).toInt().coerceIn(0, candles.lastIndex)
                        selectedIndex = if (selectedIndex == idx) null else idx
                    }
                },
        ) {
            if (size.width <= 0f || size.height <= 0f || candles.isEmpty()) return@Canvas
            val leftPad = 52.dp.toPx()
            val bottomPad = 24.dp.toPx()
            val topPad = 8.dp.toPx()
            val plotW = (size.width - leftPad - 6.dp.toPx()).coerceAtLeast(1f)
            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)

            ticks.forEach { tick ->
                val frac = ((tick - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f)
                val y = topPad + plotH - frac * plotH
                drawLine(gridColor, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1.dp.toPx(), pathEffect = dashEffect)
                val label = formatAxisTick(tick, format, unit)
                val measured = textMeasurer.measure(label, axisLabelStyle)
                drawText(
                    measured,
                    topLeft = Offset((leftPad - measured.size.width - 8.dp.toPx()).coerceAtLeast(0f), y - measured.size.height / 2f),
                )
            }

            val slotW = plotW / candles.size
            val candleW = (slotW * 0.64f).coerceIn(2.dp.toPx(), 14.dp.toPx())
            val xStep = max(1, ceil(candles.size / 5.0).toInt())

            candles.forEachIndexed { idx, c ->
                val centerX = leftPad + (idx + 0.5f) * slotW
                if (idx % xStep == 0 || idx == candles.lastIndex) {
                    val measuredDate = textMeasurer.measure(c.date, axisLabelStyle, maxLines = 1)
                    val labelX = (centerX - measuredDate.size.width / 2f).coerceIn(leftPad, size.width - measuredDate.size.width)
                    drawText(measuredDate, topLeft = Offset(labelX, topPad + plotH + 6.dp.toPx()))
                }

                if (selectedIndex == idx) {
                    drawLine(
                        color = colors.onSurface.copy(alpha = 0.2f),
                        start = Offset(centerX, topPad),
                        end = Offset(centerX, topPad + plotH),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val color = if (c.isUp) positiveColor else negativeColor
                fun yFor(v: Double) = topPad + plotH - (((v - axisMin) / axisSpan).toFloat().coerceIn(0f, 1f) * plotH)

                val highY = yFor(c.high)
                val lowY = yFor(c.low)
                drawLine(color, Offset(centerX, highY), Offset(centerX, lowY), strokeWidth = 1.25.dp.toPx(), cap = StrokeCap.Round)

                val adjustedClose = if (abs(c.close - c.open) < minBody) {
                    c.open + if (c.isUp) minBody else -minBody
                } else c.close
                val openY = yFor(c.open)
                val closeY = yFor(adjustedClose)
                val topY = min(openY, closeY)
                val bodyH = abs(closeY - openY).coerceAtLeast(1.5.dp.toPx())

                drawRoundRect(
                    color = color,
                    topLeft = Offset(centerX - candleW / 2f, topY),
                    size = Size(candleW, bodyH),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
                )
            }
        }

        selectedIndex?.let { idx ->
            val c = candles.getOrNull(idx) ?: return@let
            val dayChg = if (c.open != 0.0) ((c.close - c.open) / c.open) * 100.0 else 0.0
            val chgColor = if (c.isUp) positiveColor else negativeColor
            val sign = if (dayChg >= 0) "+" else ""
            ChartSelectionBanner(
                title = c.date,
                items = listOf(
                    Triple(colors.onSurfaceVariant, stringResource(R.string.chat_a2ui_ohlc_open), formatChartValue(c.open, format, unit)),
                    Triple(colors.onSurfaceVariant, stringResource(R.string.chat_a2ui_ohlc_high), formatChartValue(c.high, format, unit)),
                    Triple(colors.onSurfaceVariant, stringResource(R.string.chat_a2ui_ohlc_low), formatChartValue(c.low, format, unit)),
                    Triple(chgColor, stringResource(R.string.chat_a2ui_ohlc_close), "${formatChartValue(c.close, format, unit)} ($sign${String.format(Locale.US, "%.2f", dayChg)}%)"),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartSelectionBanner(
    title: String,
    items: List<Triple<Color, String, String>>,
) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val bg = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { (dotColor, label, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(6.dp).background(dotColor, CircleShape))
                    Text(
                        text = "$label $value",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatChartValue(value: Double, format: String, unit: String): String {
    val body = when (format) {
        "currency" -> if (abs(value) >= 1_000.0 && value % 1.0 == 0.0) {
            "$" + formatCompact(value)
        } else {
            "$" + amount(value)
        }
        "percent" -> "${amount(value)}%"
        "compact" -> formatCompact(value)
        else -> amount(value)
    }
    return if (unit.isNotBlank() && format != "currency" && format != "percent") "$body $unit" else body
}

private fun formatAxisTick(value: Double, format: String, unit: String): String {
    val body = when (format) {
        "currency" -> "$" + formatCompact(value)
        "percent" -> "${amount(value)}%"
        else -> formatCompact(value)
    }
    return if (unit.isNotBlank() && format != "currency" && format != "percent") "$body$unit" else body
}

private fun formatCompact(value: Double): String {
    val sign = if (value < 0) "-" else ""
    val absVal = abs(value)
    return when {
        absVal >= 1_000_000_000.0 -> sign + trimZero(String.format(Locale.US, "%.1f", absVal / 1_000_000_000.0)) + "B"
        absVal >= 1_000_000.0 -> sign + trimZero(String.format(Locale.US, "%.1f", absVal / 1_000_000.0)) + "M"
        absVal >= 1_000.0 -> sign + trimZero(String.format(Locale.US, "%.1f", absVal / 1_000.0)) + "K"
        absVal % 1.0 == 0.0 -> sign + absVal.toLong().toString()
        else -> sign + trimZero(String.format(Locale.US, "%.2f", absVal))
    }
}

private fun trimZero(text: String): String = text.removeSuffix(".0").removeSuffix(".00")

private fun computeNiceTicks(minVal: Double, maxVal: Double, desiredCount: Int): List<Double> {
    val span = (maxVal - minVal).takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    val rawStep = span / (desiredCount - 1).coerceAtLeast(1)
    val mag = 10.0.pow(floor(log10(rawStep)))
    val norm = rawStep / mag
    val niceNorm = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 5.0 -> 5.0
        else -> 10.0
    }
    val step = niceNorm * mag
    val start = floor(minVal / step) * step
    val end = ceil(maxVal / step) * step
    val ticks = mutableListOf<Double>()
    var cur = start
    var guard = 0
    while (cur <= end + step * 0.01 && guard < 10) {
        ticks += if (abs(cur) < step * 1e-9) 0.0 else cur
        cur += step
        guard++
    }
    return ticks
}

@Composable
internal fun LineGraphic(
    points: List<Pair<Double, Double>>,
    color: Color,
    modifier: Modifier,
    description: String,
    route: Boolean = false,
) {
    Canvas(modifier.fillMaxWidth().semantics { contentDescription = description }.padding(10.dp)) {
        val minX = points.minOf { it.first }
        val minY = points.minOf { it.second }
        val width = (points.maxOf { it.first } - minX).takeIf { it.isFinite() } ?: return@Canvas
        val height = (points.maxOf { it.second } - minY).takeIf { it.isFinite() } ?: return@Canvas
        val scaleX = if (width > 0) size.width / width else Double.POSITIVE_INFINITY
        val scaleY = if (height > 0) size.height / height else Double.POSITIVE_INFINITY
        val scale = min(scaleX, scaleY)
        val offsets = points.map { (x, y) ->
            val px = if (width == 0.0) center.x else if (route) center.x + ((x - minX - width / 2) * scale).toFloat()
            else ((x - minX) * scaleX).toFloat()
            val py = if (height == 0.0) center.y else if (route) center.y - ((y - minY - height / 2) * scale).toFloat()
            else size.height - ((y - minY) * scaleY).toFloat()
            Offset(px, py)
        }
        drawPath(Path().apply {
            offsets.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
        }, color, style = Stroke(if (route) 3.dp.toPx() else 2.dp.toPx()))
        if (route) {
            drawCircle(Emerald, 4.dp.toPx(), offsets.first())
            drawCircle(Violet, 4.dp.toPx(), offsets.last())
        }
    }
}
