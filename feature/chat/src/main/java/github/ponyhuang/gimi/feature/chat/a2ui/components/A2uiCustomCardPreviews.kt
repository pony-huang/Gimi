package github.ponyhuang.gimi.feature.chat.a2ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.GimiTheme

@Composable
private fun PreviewCanvas(
    darkTheme: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    GimiTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Preview(name = "A2UI charts and table", widthDp = 400)
@Composable
private fun ChartCardsPreview() {
    GimiTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BarChart(
                CardValues(
                    mapOf(
                        "title" to "Revenue by region",
                        "subtitle" to "Quarterly, USD",
                        "format" to "currency",
                        "data" to listOf(
                            mapOf("x" to "NA", "y" to 412000, "series" to "2025"),
                            mapOf("x" to "EMEA", "y" to 298000, "series" to "2025"),
                            mapOf("x" to "APAC", "y" to 186000, "series" to "2025"),
                            mapOf("x" to "LATAM", "y" to 94000, "series" to "2025"),
                            mapOf("x" to "NA", "y" to 538000, "series" to "2026"),
                            mapOf("x" to "EMEA", "y" to 371000, "series" to "2026"),
                            mapOf("x" to "APAC", "y" to 262000, "series" to "2026"),
                            mapOf("x" to "LATAM", "y" to 131000, "series" to "2026"),
                        ),
                    ),
                ),
                Modifier,
            )
            PieChart(
                CardValues(
                    mapOf(
                        "title" to "Traffic sources",
                        "subtitle" to "Sessions, last 30 days",
                        "donut" to true,
                        "centerLabel" to "sessions",
                        "format" to "compact",
                        "data" to listOf(
                            mapOf("label" to "Organic search", "value" to 48200),
                            mapOf("label" to "Direct", "value" to 27100),
                            mapOf("label" to "Referral", "value" to 14600),
                            mapOf("label" to "Social", "value" to 9800),
                            mapOf("label" to "Email", "value" to 5300),
                        ),
                    ),
                ),
                Modifier,
            )
            CandlestickChart(sampleCandlestickValues(), Modifier)
        }
    }
}

@Preview(name = "A2UI candlestick dark", widthDp = 400)
@Composable
private fun CandlestickDarkPreview() {
    GimiTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            CandlestickChart(sampleCandlestickValues(), Modifier)
        }
    }
}

@Preview(name = "A2UI data table and chips dark", widthDp = 400)
@Composable
private fun DataTableAndChipsDarkPreview() {
    PreviewCanvas(darkTheme = true) {
            DataTable(
                CardValues(
                    mapOf(
                        "title" to "Top accounts",
                        "pageSize" to 3,
                        "columns" to listOf(
                            mapOf("key" to "account", "label" to "Account"),
                            mapOf("key" to "plan", "label" to "Plan", "format" to "badge"),
                            mapOf("key" to "status", "label" to "Status", "format" to "badge"),
                            mapOf("key" to "mrr", "label" to "MRR", "format" to "currency"),
                        ),
                        "rows" to listOf(
                            mapOf("account" to "Northwind Labs", "plan" to "Enterprise", "status" to "Active", "mrr" to 12400),
                            mapOf("account" to "Helix Health", "plan" to "Enterprise", "status" to "Pending", "mrr" to 9800),
                            mapOf("account" to "Orbit Freight", "plan" to "Scale", "status" to "Overdue", "mrr" to 4300),
                            mapOf("account" to "Lumen Studio", "plan" to "Pro", "status" to "Active", "mrr" to 1240),
                        ),
                    ),
                ),
                Modifier,
            )
            ChipSelect(
                label = "Notify me about",
                selectedValues = listOf("deploys", "incidents"),
                options = listOf(
                    "deploys" to "Deploys",
                    "incidents" to "Incidents",
                    "billing" to "Billing",
                    "security" to "Security",
                    "usage" to "Usage spikes",
                ),
                update = {},
            )
    }
}

private fun sampleCandlestickValues(): CardValues = CardValues(
    mapOf(
        "title" to "Halvorsen Robotics (HLVR)",
        "subtitle" to "Daily OHLC, September 2026",
        "format" to "currency",
        "data" to listOf(
            mapOf("date" to "Sep 1", "open" to 182.4, "high" to 182.48, "low" to 178.71, "close" to 179.46),
            mapOf("date" to "Sep 2", "open" to 179.46, "high" to 179.86, "low" to 177.23, "close" to 178.2),
            mapOf("date" to "Sep 3", "open" to 178.2, "high" to 181.54, "low" to 178.1, "close" to 181.5),
            mapOf("date" to "Sep 4", "open" to 181.5, "high" to 182.76, "low" to 179.19, "close" to 180.2),
            mapOf("date" to "Sep 5", "open" to 180.2, "high" to 183.17, "low" to 179.39, "close" to 183.02),
            mapOf("date" to "Sep 10", "open" to 182.35, "high" to 182.47, "low" to 182.19, "close" to 182.38),
            mapOf("date" to "Sep 12", "open" to 180.93, "high" to 185.67, "low" to 180.02, "close" to 184.34),
            mapOf("date" to "Sep 19", "open" to 178.44, "high" to 180.02, "low" to 175.86, "close" to 176.07),
            mapOf("date" to "Sep 27", "open" to 177.98, "high" to 181.81, "low" to 177.12, "close" to 181.33),
            mapOf("date" to "Sep 30", "open" to 180.12, "high" to 181.7, "low" to 179.54, "close" to 180.17),
        ),
    ),
)

@Preview(name = "A2UI travel and agenda", widthDp = 400)
@Composable
private fun TravelCardsPreview() {
    PreviewCanvas(darkTheme = false) {
        Timeline(
            CardValues(
                mapOf(
                    "title" to "物流进度",
                    "items" to listOf(
                        mapOf("title" to "订单已确认", "time" to "10-14 09:42", "status" to "done"),
                        mapOf("title" to "仓库打包完成", "time" to "10-14 16:10", "description" to "共 3 件商品，2.4 kg", "status" to "done"),
                        mapOf("title" to "干线运输中", "time" to "10-15 07:05", "description" to "已离开杭州转运中心", "status" to "current", "icon" to "truck"),
                        mapOf("title" to "派送中", "time" to "预计 10-16", "status" to "upcoming"),
                    ),
                ),
            ),
            Modifier,
        )
        Agenda(
            CardValues(
                mapOf(
                    "title" to "本周日程",
                    "days" to listOf(
                        mapOf(
                            "date" to "2026-10-15",
                            "label" to "今天",
                            "events" to listOf(
                                mapOf("time" to "09:30", "title" to "设计评审：计费页", "location" to "4B 会议室", "tone" to "accent"),
                                mapOf("time" to "13:00", "title" to "团队午餐", "location" to "湖滨店", "tone" to "positive"),
                            ),
                        ),
                        mapOf(
                            "date" to "2026-10-17",
                            "events" to emptyList<Map<String, Any>>(),
                        ),
                    ),
                ),
            ),
            Modifier,
        )
        ChipSelect(
            label = "通知订阅",
            selectedValues = listOf("deploys", "incidents"),
            options = listOf(
                "deploys" to "部署发布",
                "incidents" to "线上告警",
                "billing" to "账单通知",
                "security" to "安全审计",
            ),
            update = {},
        )
        SettingSwitch("出发提醒", "提前 30 分钟提醒", checked = true, update = {}, modifier = Modifier)
    }
}

@Preview(name = "A2UI receipt and quote", widthDp = 400)
@Composable
private fun RecordCardsPreview() {
    PreviewCanvas(darkTheme = false) {
        ReceiptCard(
            CardValues(
                mapOf(
                    "merchant" to "Fieldnote Supply Co.",
                    "orderId" to "#FN-48213",
                    "date" to "Oct 14, 2026",
                    "status" to "Paid",
                    "currency" to "USD",
                    "items" to listOf(
                        mapOf("name" to "Dot grid notebook, A5", "qty" to 2, "price" to 18),
                        mapOf("name" to "Brass fountain pen", "qty" to 1, "price" to 64, "note" to "Fine nib, engraved"),
                    ),
                    "shipping" to 0,
                    "discount" to 10,
                    "tax" to 9.62,
                    "payment" to "Visa ending 4242",
                ),
            ),
            Modifier,
        )
        KeyValueList(
            CardValues(
                mapOf(
                    "title" to "Order #A-20481",
                    "items" to listOf(
                        mapOf("label" to "Placed", "value" to "Oct 14, 2026", "hint" to "9:42 AM PT"),
                        mapOf("label" to "Items", "value" to 3),
                        mapOf("label" to "Total", "value" to "$236.40", "hint" to "Incl. $18.40 tax"),
                    ),
                ),
            ),
            Modifier,
        )
        StockTicker(
            CardValues(
                mapOf(
                    "name" to "Kestrel Robotics Inc.",
                    "symbol" to "HLVR",
                    "currency" to "USD",
                    "price" to 182.47,
                    "change" to 3.21,
                    "changePct" to 1.79,
                    "sample" to true,
                    "series" to listOf(176.2, 177.1, 176.4, 178.3, 177.9, 179.6, 179.1, 180.4, 179.8, 181.2, 182.47),
                    "stats" to listOf(
                        mapOf("label" to "Open", "value" to "179.40"),
                        mapOf("label" to "High", "value" to "183.12"),
                        mapOf("label" to "Volume", "value" to "24.1M"),
                        mapOf("label" to "P/E", "value" to "31.4"),
                    ),
                ),
            ),
            Modifier,
        )
    }
}

@Preview(name = "A2UI music player light", widthDp = 400)
@Composable
private fun MusicPlayerLightPreview() {
    PreviewCanvas(darkTheme = false) {
        MusicPlayerCard(CardValues(mapOf(
            "title" to "Goon Gumpas",
            "artist" to "Aphex Twin",
            "album" to "Richard D. James Album",
            "lyric" to "♪ 轻快弦乐与电子节拍交织",
            "durationSeconds" to 153,
            "audioUrl" to "https://example.com/goon-gumpas.mp3",
            "songId" to "8f3c9a12b4e567890123456789abcdef",
            "originalId" to "139774",
            "liked" to true,
            "playlistTitle" to "Aphex Twin 精选歌单",
            "tracks" to listOf(
                mapOf("title" to "Goon Gumpas", "artist" to "Aphex Twin", "durationSeconds" to 153, "liked" to true),
                mapOf("title" to "Avril 14th", "artist" to "Aphex Twin", "durationSeconds" to 125),
                mapOf("title" to "Windowlicker", "artist" to "Aphex Twin", "durationSeconds" to 45, "isTrial" to true, "isVip" to true),
            ),
        )), Modifier)
    }
}

@Preview(name = "A2UI music player and queue dark", widthDp = 400)
@Composable
private fun MusicPlayerDarkPreview() {
    GimiTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicPlayerCard(CardValues(mapOf(
                "title" to "Windowlicker",
                "artist" to "Aphex Twin",
                "album" to "Windowlicker EP",
                "durationSeconds" to 45,
                "songId" to "7c8d9e0f1a2b34567890123456789abc",
                "originalId" to "139815",
                "isTrial" to true,
                "isVip" to true,
            )), Modifier)
            MusicQueueSheetContent(
                playlistTitle = "Aphex Twin 精选歌单",
                tracks = listOf(
                    MusicTrackItem(title = "Goon Gumpas", artist = "Aphex Twin", album = "Richard D. James Album", durationSeconds = 153, liked = true),
                    MusicTrackItem(title = "Avril 14th", artist = "Aphex Twin", album = "Drukqs", durationSeconds = 125),
                    MusicTrackItem(title = "Windowlicker", artist = "Aphex Twin", durationSeconds = 45, isTrial = true, isVip = true),
                ),
                activeIndex = 1,
                palette = fallbackPaletteFor("Avril 14th", 1),
                onSelectTrack = {},
                onClose = {},
            )
        }
    }
}

@Preview(name = "A2UI weather card light", widthDp = 400)
@Composable
private fun WeatherCardLightPreview() {
    GimiTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            WeatherCard(sampleWeatherCardValues(), Modifier)
        }
    }
}

@Preview(name = "A2UI weather card dark", widthDp = 400)
@Composable
private fun WeatherCardDarkPreview() {
    GimiTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            WeatherCard(sampleWeatherCardValues(), Modifier)
        }
    }
}

@Preview(name = "A2UI entity card light", widthDp = 400)
@Composable
private fun EntityCardLightPreview() {
    PreviewCanvas(darkTheme = false) {
        EntityCard(
            title = "Café Central",
            subtitle = "Coffee house · 1st district",
            description = "Grand historic café known for its vaulted interior and literary legacy.",
            imageUrl = "",
            meta = "Open until 22:00 · €€",
            badge = EntityBadge(label = "Popular", color = "success"),
            rating = 4.5,
            ratingCount = 12840,
            actions = listOf(
                EntityAction(label = "Directions", href = "https://maps.google.com"),
                EntityAction(label = "Pick", variant = "solid"),
            ),
        )
    }
}

@Preview(name = "A2UI entity card horizontal dark", widthDp = 400)
@Composable
private fun EntityCardHorizontalDarkPreview() {
    PreviewCanvas(darkTheme = true) {
        EntityCard(
            title = "Café Central",
            subtitle = "Coffee house · 1st district",
            description = "Grand historic café known for its vaulted interior and literary legacy.",
            imageUrl = "",
            meta = "Open until 22:00 · €€",
            badge = EntityBadge(label = "Popular", color = "success"),
            rating = 4.5,
            ratingCount = 12840,
            horizontal = true,
            actions = listOf(
                EntityAction(label = "Directions", href = "https://maps.google.com"),
                EntityAction(label = "Pick", variant = "solid"),
            ),
        )
    }
}

private fun sampleWeatherCardValues(): CardValues = CardValues(
    mapOf(
        "location" to "San Francisco, CA",
        "temp" to 18,
        "condition" to "Partly cloudy",
        "icon" to "cloud-sun",
        "high" to 21,
        "low" to 12,
        "feels" to 17,
        "humidity" to 68,
        "wind" to 19,
        "unit" to "C",
        "hourly" to listOf(
            mapOf("label" to "Now", "temp" to 18, "icon" to "cloud-sun"),
            mapOf("label" to "2 PM", "temp" to 20, "icon" to "sun"),
            mapOf("label" to "3 PM", "temp" to 21, "icon" to "sun"),
            mapOf("label" to "5 PM", "temp" to 18, "icon" to "cloud", "precip" to 10),
            mapOf("label" to "7 PM", "temp" to 15, "icon" to "drizzle", "precip" to 40),
        ),
        "daily" to listOf(
            mapOf("label" to "Today", "min" to 12, "max" to 21, "icon" to "cloud-sun"),
            mapOf("label" to "Thu", "min" to 11, "max" to 17, "icon" to "rain", "precip" to 70),
            mapOf("label" to "Fri", "min" to 10, "max" to 15, "icon" to "storm", "precip" to 85),
            mapOf("label" to "Sat", "min" to 11, "max" to 19, "icon" to "cloud"),
            mapOf("label" to "Sun", "min" to 13, "max" to 24, "icon" to "sun"),
        ),
    ),
)
