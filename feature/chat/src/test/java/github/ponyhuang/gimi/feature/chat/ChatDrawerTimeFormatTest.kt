package github.ponyhuang.gimi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 抽屉历史会话单行紧凑相对时间格式化单测。
 */
class ChatDrawerTimeFormatTest {

    @Test
    fun formatsJustNowWhenUnderOneMinute() {
        val now = 1_000_000_000L
        assertEquals("刚刚", formatCompactTimeRaw(timestamp = now - 30_000L, now = now))
        assertEquals("刚刚", formatCompactTimeRaw(timestamp = now, now = now))
    }

    @Test
    fun formatsMinutesWhenUnderOneHour() {
        val now = 1_000_000_000L
        assertEquals("1m", formatCompactTimeRaw(timestamp = now - 60_000L, now = now))
        assertEquals("3m", formatCompactTimeRaw(timestamp = now - 180_000L, now = now))
        assertEquals("59m", formatCompactTimeRaw(timestamp = now - 59 * 60_000L, now = now))
    }

    @Test
    fun formatsHoursWhenUnderOneDay() {
        val now = 1_000_000_000L
        assertEquals("1h", formatCompactTimeRaw(timestamp = now - 3_600_000L, now = now))
        assertEquals("6h", formatCompactTimeRaw(timestamp = now - 6 * 3_600_000L, now = now))
        assertEquals("23h", formatCompactTimeRaw(timestamp = now - 23 * 3_600_000L, now = now))
    }

    @Test
    fun formatsDaysWhenUnderThirtyDays() {
        val now = 1_000_000_000L
        assertEquals("1d", formatCompactTimeRaw(timestamp = now - 86_400_000L, now = now))
        assertEquals("2d", formatCompactTimeRaw(timestamp = now - 2 * 86_400_000L, now = now))
        assertEquals("29d", formatCompactTimeRaw(timestamp = now - 29 * 86_400_000L, now = now))
    }

    @Test
    fun formatsMonthsWhenUnderOneYear() {
        val now = 2_000_000_000L
        assertEquals("1mo", formatCompactTimeRaw(timestamp = now - 30L * 86_400_000L, now = now))
        assertEquals("3mo", formatCompactTimeRaw(timestamp = now - 95L * 86_400_000L, now = now))
    }

    @Test
    fun formatsYearsWhenOverOneYear() {
        val now = 2_000_000_000L
        assertEquals("1y", formatCompactTimeRaw(timestamp = now - 366L * 86_400_000L, now = now))
        assertEquals("2y", formatCompactTimeRaw(timestamp = now - 750L * 86_400_000L, now = now))
    }

    @Test
    fun handlesClockSkewGracefully() {
        val now = 1_000_000_000L
        // 时间戳在未来时自动钳位为 0，展示刚刚
        assertEquals("刚刚", formatCompactTimeRaw(timestamp = now + 60_000L, now = now))
    }
}
