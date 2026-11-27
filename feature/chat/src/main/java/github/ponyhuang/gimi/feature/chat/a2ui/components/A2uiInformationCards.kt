package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.LocalGimiDarkTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle as JavaTimeTextStyle
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

internal val Violet = Color(0xFF7C3AED)
internal val Sky = Color(0xFF0284C7)
internal val Emerald = Color(0xFF047857)
internal val Rose = Color(0xFFBE123C)

@Composable
internal fun ComponentCard(
    title: String,
    modifier: Modifier,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = tint?.copy(alpha = .09f) ?: Color.Transparent
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = container, contentColor = colors.onSurface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun Detail(text: String, modifier: Modifier = Modifier) {
    if (text.isNotBlank()) Text(text, modifier, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LabelValue(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Detail(label, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun amount(value: Double, currency: String = ""): String {
    val code = if (currency.length == 3) {
        try { Currency.getInstance(currency.uppercase()) } catch (_: IllegalArgumentException) { null }
    } else null
    val format = if (code == null) {
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    } else {
        NumberFormat.getCurrencyInstance().apply {
            this.currency = code
            val digits = code.defaultFractionDigits.coerceAtLeast(0)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
    }
    return format.format(value)
}

@Composable
private fun StatusPill(text: String, color: Color) {
    if (text.isBlank()) return
    Text(text, Modifier.background(color.copy(alpha = .13f), RoundedCornerShape(24.dp))
        .padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
}

@Composable
internal fun KeyValueList(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val title = values.text("title")
    val items = values.rows("items")
    val cols = (values.number("columns")?.toInt() ?: 1).coerceIn(1, 2)

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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            val rowGroups = items.chunked(cols)
            rowGroups.forEachIndexed { rowIdx, group ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    group.forEach { item ->
                        val rawVal = item.cell("value")
                        val valText = when (rawVal) {
                            is Number -> amount(rawVal.toDouble())
                            else -> item.text("value")
                        }
                        val hint = item.text("hint")
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = item.text("label"),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = colors.onSurfaceVariant,
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = valText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = colors.onSurface,
                                    textAlign = TextAlign.End,
                                )
                                if (hint.isNotBlank()) {
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                                        textAlign = TextAlign.End,
                                    )
                                }
                            }
                        }
                    }
                    repeat(cols - group.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                if (rowIdx < rowGroups.lastIndex) {
                    DashedHorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun DashedHorizontalDivider(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
        )
    }
}

@Composable
internal fun Timeline(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val items = values.rows("items")
    val title = values.text("title")

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
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items.forEachIndexed { index, event ->
                val status = event.text("status").ifBlank { "done" }
                val isLast = index == items.lastIndex
                val isDone = status == "done"
                val isCurrent = status == "current" || status == "active"
                val iconName = event.text("icon")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.width(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val nodeBg = when {
                            isDone -> Emerald.copy(alpha = 0.18f)
                            isCurrent -> Violet.copy(alpha = 0.16f)
                            else -> cardBg
                        }
                        val nodeBorder = when {
                            isDone -> Color.Transparent
                            isCurrent -> Violet
                            else -> colors.outlineVariant
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(20.dp)
                                .background(nodeBg, CircleShape)
                                .border(1.dp, nodeBorder, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                iconName.equals("truck", ignoreCase = true) -> Icon(
                                    imageVector = Icons.Rounded.LocalShipping,
                                    contentDescription = null,
                                    tint = if (isCurrent) Violet else if (isDone) Emerald else colors.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                                isDone -> Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Emerald,
                                    modifier = Modifier.size(12.dp),
                                )
                                isCurrent -> Box(Modifier.size(6.dp).background(Violet, CircleShape))
                                else -> Box(Modifier.size(6.dp).background(colors.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                            }
                        }
                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .weight(1f)
                                    .padding(top = 4.dp)
                                    .background(if (isDone) Emerald.copy(alpha = 0.45f) else colors.outlineVariant),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isLast) 0.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = event.text("title"),
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = if (!isDone && !isCurrent) colors.onSurface.copy(alpha = 0.65f) else colors.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val time = event.text("time")
                            if (time.isNotBlank()) {
                                Text(
                                    text = time,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                        val description = event.text("description")
                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Agenda(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val wellBg = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLow
    val title = values.text("title")
    val days = values.rows("days")
    val locale = Locale.getDefault()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
            }

            days.forEachIndexed { dayIndex, day ->
                if (dayIndex > 0) {
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                }
                val rawDate = day.text("date")
                val parsed = remember(rawDate) {
                    runCatching { LocalDate.parse(rawDate) }.getOrNull()
                }
                val topLabel = day.text("label").ifBlank {
                    parsed?.dayOfWeek?.getDisplayName(JavaTimeTextStyle.SHORT, locale).orEmpty()
                }
                val dayNumber = parsed?.dayOfMonth?.toString() ?: rawDate
                val monthLabel = parsed?.month?.getDisplayName(JavaTimeTextStyle.SHORT, locale).orEmpty()
                val events = day.rows("events", 20)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(
                        modifier = Modifier.width(44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (topLabel.isNotBlank()) {
                            Text(
                                text = topLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = dayNumber,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.onSurface,
                            maxLines = 1,
                        )
                        if (monthLabel.isNotBlank()) {
                            Text(
                                text = monthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (events.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_a2ui_agenda_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        } else {
                            events.forEach { event ->
                                val toneColor = when (event.text("tone").lowercase()) {
                                    "positive" -> Emerald
                                    "warning" -> Color(0xFFF59E0B)
                                    "info" -> Sky
                                    else -> Violet
                                }
                                val timeText = event.text("time").ifBlank {
                                    listOf(event.text("start"), event.text("end"))
                                        .filter(String::isNotBlank)
                                        .joinToString("–")
                                        .ifBlank { stringResource(R.string.chat_a2ui_agenda_all_day) }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .background(wellBg, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.5.dp)
                                            .fillMaxHeight()
                                            .background(toneColor, CircleShape),
                                    )
                                    Text(
                                        text = timeText,
                                        modifier = Modifier.width(56.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = colors.onSurfaceVariant,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = event.text("title"),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            color = colors.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val speaker = event.text("speaker")
                                        if (speaker.isNotBlank()) Detail(speaker)
                                        val description = event.text("description")
                                        if (description.isNotBlank()) Detail(description)
                                        val location = event.text("location")
                                        if (location.isNotBlank()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.LocationOn,
                                                    contentDescription = null,
                                                    tint = colors.onSurfaceVariant,
                                                    modifier = Modifier.size(11.dp),
                                                )
                                                Text(
                                                    text = location,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitch(
    label: String,
    description: String,
    checked: Boolean,
    update: ((Boolean?) -> Unit)?,
    modifier: Modifier,
) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val wellBg = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLow

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = wellBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = update != null,
                    role = Role.Switch,
                    onValueChange = { update?.invoke(it) },
                )
                .heightIn(min = 52.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurface,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = update != null,
            )
        }
    }
}

@Composable
internal fun ReceiptCard(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val wellBg = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLow

    val currency = values.text("currency").ifBlank { "USD" }
    val items = values.rows("items")
    val subtotal = items.sumOf { (it.number("qty") ?: 1.0) * (it.number("price") ?: 0.0) }
    val tax = values.number("tax") ?: 0.0
    val shipping = values.number("shipping")
    val discount = abs(values.number("discount") ?: 0.0)
    val total = subtotal + tax + (shipping ?: 0.0) - discount

    val status = values.text("status")
    val statusColor = when {
        status.matches(Regex(".*(paid|complete|deliver|success|confirm|已付款|已完成).*", RegexOption.IGNORE_CASE)) -> Emerald
        status.matches(Regex(".*(refund|cancel|fail|退款|取消|失败).*", RegexOption.IGNORE_CASE)) -> Rose
        else -> Color(0xFFF59E0B)
    }
    val meta = listOfNotNull(
        values.text("orderId").takeIf { it.isNotBlank() },
        values.text("date").takeIf { it.isNotBlank() },
    ).joinToString("  ·  ")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(wellBg, RoundedCornerShape(10.dp))
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ReceiptLong,
                            contentDescription = null,
                            tint = Violet,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = values.text("merchant"),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.13f), CircleShape)
                            .border(1.dp, statusColor.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = statusColor,
                    )
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items.forEach { item ->
                    val qty = item.number("qty") ?: 1.0
                    val linePrice = qty * (item.number("price") ?: 0.0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 20.dp)
                                    .widthIn(min = 20.dp)
                                    .background(wellBg, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = amount(qty),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Column {
                                Text(
                                    text = item.text("name"),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = colors.onSurface,
                                )
                                val note = item.text("note")
                                if (note.isNotBlank()) Detail(note)
                            }
                        }
                        Text(
                            text = amount(linePrice, currency),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = colors.onSurface,
                        )
                    }
                }
            }

            DashedHorizontalDivider(
                color = colors.outlineVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ReceiptSummaryRow(stringResource(R.string.chat_a2ui_subtotal), amount(subtotal, currency))
                if (shipping != null) {
                    val shipText = if (shipping == 0.0) stringResource(R.string.chat_a2ui_shipping_free) else amount(shipping, currency)
                    ReceiptSummaryRow(stringResource(R.string.chat_a2ui_shipping), shipText)
                }
                if (discount > 0.0) {
                    ReceiptSummaryRow(stringResource(R.string.chat_a2ui_discount), "-${amount(discount, currency)}", valueColor = Emerald)
                }
                if (tax > 0.0) {
                    ReceiptSummaryRow(stringResource(R.string.chat_a2ui_tax), amount(tax, currency))
                }

                HorizontalDivider(
                    color = colors.outlineVariant.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = stringResource(R.string.chat_a2ui_total),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        text = amount(total, currency),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
            }

            val payment = values.text("payment")
            if (payment.isNotBlank()) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(wellBg.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreditCard,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = payment,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptSummaryRow(label: String, value: String, valueColor: Color? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
            color = colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                fontFeatureSettings = "tnum",
            ),
            color = valueColor ?: colors.onSurface,
        )
    }
}

@Composable
internal fun StockTicker(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val wellBg = if (darkTheme) colors.surfaceContainer else colors.surfaceContainerLow

    val name = values.text("name")
    val symbol = values.text("symbol")
    val currency = values.text("currency").ifBlank { "USD" }
    val price = values.number("price")
    val change = values.number("change")
    val percent = values.number("changePct")
    val series = values.list("series").mapNotNull { (it as? Number)?.toDouble()?.takeIf(Double::isFinite) }
    val isUp = (change ?: percent ?: if (series.size > 1) series.last() - series.first() else 0.0) >= 0.0
    val trendColor = if (isUp) {
        if (darkTheme) Color(0xFF34D399) else Emerald
    } else {
        if (darkTheme) Color(0xFFF87171) else Rose
    }
    val stats = values.rows("stats")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 32.dp)
                            .background(wellBg, RoundedCornerShape(9.dp))
                            .border(1.dp, colors.outlineVariant.copy(alpha = 0.75f), RoundedCornerShape(9.dp))
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = symbol.take(4),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = colors.onSurface,
                        )
                    }
                    Column {
                        Text(
                            text = symbol,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (name.isNotBlank()) Detail(name)
                    }
                }
                if (values.flag("sample")) {
                    val amber = Color(0xFFF59E0B)
                    Text(
                        text = stringResource(R.string.chat_a2ui_sample),
                        modifier = Modifier
                            .background(amber.copy(alpha = 0.12f), CircleShape)
                            .border(1.dp, amber.copy(alpha = 0.32f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = amber,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = price?.let { amount(it, currency) } ?: "--",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.onSurface,
                )
                if (change != null || percent != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = if (isUp) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(16.dp),
                        )
                        val chgText = buildString {
                            if (change != null) {
                                append(if (change >= 0) "+" else "")
                                append(String.format(Locale.US, "%.2f", change))
                            }
                            if (percent != null) {
                                if (isNotEmpty()) append(" ")
                                append("(${if (percent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", percent)}%)")
                            }
                        }
                        Text(
                            text = chgText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum",
                            ),
                            color = trendColor,
                        )
                    }
                }
            }

            if (series.size > 1) {
                val description = stringResource(R.string.chat_a2ui_stock_trend, amount(series.first(), currency), amount(series.last(), currency))
                StockSparklineArea(
                    series = series,
                    color = trendColor,
                    baselineColor = colors.onSurface.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .semantics { contentDescription = description },
                )
            }

            if (stats.isNotEmpty()) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    stats.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            pair.forEach { stat ->
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stat.text("label"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stat.text("value"),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = colors.onSurface,
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockSparklineArea(
    series: List<Double>,
    color: Color,
    baselineColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f || series.size < 2) return@Canvas
        val padY = 4.dp.toPx()
        val minV = series.min()
        val maxV = series.max()
        val span = (maxV - minV).takeIf { it > 0 } ?: 1.0
        val usableH = (size.height - padY * 2f).coerceAtLeast(1f)
        fun yFor(v: Double) = padY + (1f - ((v - minV) / span).toFloat()) * usableH

        val baseY = yFor(series.first())
        drawLine(
            color = baselineColor,
            start = Offset(0f, baseY),
            end = Offset(size.width, baseY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()), 0f),
        )

        val linePath = Path()
        val areaPath = Path()
        series.forEachIndexed { idx, value ->
            val x = (idx.toFloat() / (series.size - 1)) * size.width
            val y = yFor(value)
            if (idx == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, size.height)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(size.width, size.height)
        areaPath.close()

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
internal fun DataTable(values: CardValues, modifier: Modifier) {
    val darkTheme = LocalGimiDarkTheme.current
    val colors = MaterialTheme.colorScheme
    val cardBg = if (darkTheme) colors.surfaceContainerLow else colors.surfaceContainerLowest
    val columns = values.rows("columns").filter { it.text("key").isNotBlank() }
    val source = values.rows("rows")
    var sortKey by rememberSaveable { mutableStateOf<String?>(null) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf(0) }
    val pageSize = (values.number("pageSize")?.toInt() ?: 8).coerceAtLeast(1)

    val sortedRows = source.let { rows ->
        val key = sortKey ?: return@let rows
        rows.sortedWith { a, b ->
            val order = compareTableCells(a.cell(key), b.cell(key))
            if (descending) -order.sign() else order
        }
    }

    val totalCount = sortedRows.size
    val totalPages = ceil(totalCount.toDouble() / pageSize).toInt().coerceAtLeast(1)
    val currentPage = page.coerceIn(0, totalPages - 1)
    val startIdx = currentPage * pageSize
    val pageRows = if (totalCount == 0) emptyList() else sortedRows.subList(startIdx, minOf(startIdx + pageSize, totalCount))
    val title = values.text("title")

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cardBg,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank() || totalCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.chat_a2ui_table_rows, totalCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
            }

            Column(Modifier.fillMaxWidth().boundedHorizontalScroll().width((114.dp * columns.size).coerceAtLeast(300.dp))) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    columns.forEach { column ->
                        val key = column.text("key")
                        val format = column.text("format")
                        val alignRight = column.text("align") == "right" || format in setOf("number", "currency", "percent")
                        val selected = sortKey == key
                        val sortState = if (!selected) "" else stringResource(
                            if (descending) R.string.chat_a2ui_sort_desc else R.string.chat_a2ui_sort_asc,
                        )
                        Text(
                            text = column.text("label") + if (!selected) " ↕" else if (descending) " ↓" else " ↑",
                            modifier = Modifier
                                .width(114.dp)
                                .heightIn(min = 40.dp)
                                .clickable {
                                    descending = selected && !descending
                                    sortKey = key
                                }
                                .semantics { if (selected) stateDescription = sortState }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurfaceVariant,
                            textAlign = if (alignRight) TextAlign.End else TextAlign.Start,
                        )
                    }
                }
                pageRows.forEach { row ->
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        columns.forEach { column ->
                            val key = column.text("key")
                            val format = column.text("format")
                            val alignRight = column.text("align") == "right" || format in setOf("number", "currency", "percent")
                            val rawCell = row.cell(key)
                            val formatted = formatTableCell(rawCell, format)

                            Box(
                                modifier = Modifier
                                    .width(114.dp)
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                contentAlignment = if (alignRight) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                if (format == "badge" && formatted.isNotBlank()) {
                                    TableStatusBadge(formatted)
                                } else {
                                    Text(
                                        text = formatted,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFeatureSettings = if (alignRight) "tnum" else null,
                                        ),
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (pageRows.isEmpty()) {
                Detail(
                    stringResource(R.string.chat_a2ui_table_empty),
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (totalPages > 1) {
                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.65f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_a2ui_table_range, startIdx + 1, minOf(startIdx + pageSize, totalCount), totalCount),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { page = (currentPage - 1).coerceAtLeast(0) },
                            enabled = currentPage > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.chat_a2ui_prev_page), modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "${currentPage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = colors.onSurfaceVariant,
                        )
                        IconButton(
                            onClick = { page = (currentPage + 1).coerceAtMost(totalPages - 1) },
                            enabled = currentPage < totalPages - 1,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.chat_a2ui_next_page), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableStatusBadge(text: String) {
    val badgeColor = when (text.lowercase()) {
        "active", "done", "paid", "completed" -> Emerald
        "pending", "trial", "warning" -> Color(0xFFF59E0B)
        "overdue", "failed", "error", "cancelled" -> Rose
        "scale", "pro", "info" -> Sky
        else -> Violet
    }
    Text(
        text = text,
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, badgeColor.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = badgeColor,
        maxLines = 1,
    )
}

private fun formatTableCell(value: Any?, format: String): String {
    if (value == null) return ""
    val num = (value as? Number)?.toDouble()
    return when (format) {
        "currency" -> if (num != null) "$" + amount(num) else value.toString()
        "percent" -> if (num != null) "${amount(num)}%" else value.toString()
        "number" -> if (num != null) amount(num) else value.toString()
        else -> tableCell(value)
    }
}

private fun Int.sign(): Int = compareTo(0)

private fun tableCell(value: Any?): String = when (value) {
    is Number -> amount(value.toDouble())
    else -> value?.toString().orEmpty()
}

/** 数据类型混合时仍保持统一的排序顺序，避免违反比较器传递性。 */
private fun compareTableCells(a: Any?, b: Any?): Int = when {
    a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
    a is Number -> -1
    b is Number -> 1
    else -> tableCell(a).compareTo(tableCell(b), ignoreCase = true)
}

/** 标准横向 List 的条目可能收到无限宽，先补足内部表格和逐小时预报的视口。 */
@Composable
internal fun Modifier.boundedHorizontalScroll(): Modifier =
    widthIn(max = LocalConfiguration.current.screenWidthDp.coerceAtLeast(1).dp)
        .horizontalScroll(rememberScrollState())
