package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileClickTargetTest {
    @Test
    fun keepsRowLabelsForDuplicateButtonsAndOmitsCoveredOrSensitiveControls() {
        val first = node("first", listOf(0, 1), "Open", y = 10)
        val second = node("second", listOf(1, 1), "Open", y = 30)
        val firstRow = node("first-row", listOf(0, 0), "Alice", y = 10, clickable = false)
        val secondRow = node("second-row", listOf(1, 0), "Bob", y = 30, clickable = false)
        val secret = node("password", listOf(2, 0), "secret", y = 40).copy(sensitive = true)
        val covered = node("covered", listOf(3, 0), "Delete", y = 50)
        val snapshot = MobileAccessibilitySnapshot(
            status = "available",
            windows = listOf(MobileWindowState(2, 1, MobileBounds(0, 50, 20, 70))),
            targets = listOf(first, second, firstRow, secondRow, secret, covered).associateBy { it.element.id },
        )
        assertEquals(listOf("Open" to "Alice", "Open" to "Bob"), snapshot.clickTargets().map { it.label to it.context })
    }

    @Test
    fun describesAnEditableControlWithoutItsEnteredValue() {
        val target = node("search", listOf(0, 0), "private query", y = 10)
        val field = target.ownElement.copy(editable = true, description = "Search box")
        val snapshot = MobileAccessibilitySnapshot("available",
            targets = mapOf("search" to target.copy(element = field, ownElement = field, clickElement = field)))
        assertEquals("Search box", snapshot.clickTargets().single().label)
    }

    private fun node(id: String, path: List<Int>, text: String, y: Int, clickable: Boolean = true): MobileNodeTarget {
        val element = MobileElement(id, "accessibility", MobileBounds(0, y, 20, y + 10),
            text = text, windowId = 1, windowLayer = 0)
        return MobileNodeTarget(element, path, element, path.takeIf { clickable }, element.takeIf { clickable })
    }
}
