package github.ponyhuang.gimi.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 抽屉高度解析。
 *
 * 这里锁住的是「不能全屏」这条硬约束：无论调用方传入什么高度，抽屉都不会长过
 * 窗口的九成；另外锁住外壳高度的预留与实测切换，避免主体上限被首帧预留值永久压低。
 */
class GimiSheetHeightTest {

    @Test
    fun `比例高度按窗口高度换算`() {
        val heights = resolveSheetHeights(
            windowHeight = 800.dp,
            initialHeight = SheetHeight.Fraction(0.5f),
            maxHeight = SheetHeight.Fraction(0.9f),
        )

        assertEquals(400.dp, heights.opening)
        assertEquals(720.dp, heights.max)
    }

    @Test
    fun `绝对高度不受窗口比例影响`() {
        val heights = resolveSheetHeights(
            windowHeight = 800.dp,
            initialHeight = SheetHeight.Absolute(240.dp),
            maxHeight = SheetHeight.Absolute(600.dp),
        )

        assertEquals(240.dp, heights.opening)
        assertEquals(600.dp, heights.max)
    }

    @Test
    fun `最大高度被压到窗口九成`() {
        val heights = resolveSheetHeights(
            windowHeight = 800.dp,
            initialHeight = SheetHeight.Fraction(0.5f),
            // 调用方想给全屏，组件不让。
            maxHeight = SheetHeight.Absolute(2000.dp),
        )

        assertEquals(720.dp, heights.max)
    }

    @Test
    fun `打开高度超过上限时被压回上限`() {
        val heights = resolveSheetHeights(
            windowHeight = 800.dp,
            initialHeight = SheetHeight.Fraction(0.95f),
            maxHeight = SheetHeight.Fraction(0.9f),
        )

        assertEquals(720.dp, heights.opening)
    }

    @Test
    fun `外部指定的高度优先于初始高度`() {
        val heights = resolveSheetHeights(
            windowHeight = 800.dp,
            initialHeight = SheetHeight.Fraction(0.5f),
            maxHeight = SheetHeight.Fraction(0.9f),
            requestedHeight = SheetHeight.Fraction(0.7f),
        )

        assertEquals(560.dp, heights.opening)
    }

    @Test
    fun `首帧还没有实测值时用预留高度`() {
        assertEquals(240.dp, resolveChromeHeight(headerHeight = null, footerHeight = null))
        assertEquals(240.dp, resolveChromeHeight(headerHeight = 60.dp, footerHeight = null))
    }

    @Test
    fun `实测高度小于预留值时不把预留值当下限`() {
        // 横杆 32 + 标题 60 + 底部 48；实测后主体上限不再被 240dp 压住。
        val chrome = resolveChromeHeight(headerHeight = 60.dp, footerHeight = 48.dp)
        assertEquals(140.dp, chrome)
        assertEquals(580.dp, resolveBodyLimit(720.dp, chrome))
    }

    @Test
    fun `实测高度大于预留值时按实测值扣`() {
        // 大字号下标题与搜索行可能比预留值还高，此时以实测为准，footer 不会被挤掉。
        val chrome = resolveChromeHeight(headerHeight = 180.dp, footerHeight = 90.dp)
        assertEquals(302.dp, chrome)
    }

    @Test
    fun `窗口再小主体高度也不会是负数`() {
        assertEquals(0.dp, resolveBodyLimit(120.dp, 240.dp))
        assertEquals(40.dp, resolveBodyLimit(180.dp, 140.dp))
    }
}
