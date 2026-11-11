package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement

import android.os.Bundle
import android.util.SparseArray
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import android.os.SystemClock
import io.mockk.mockkStatic

class MobileTextAccessibilityServiceTest {
    private val service = spyk(MobileTextAccessibilityService())
    private val node = mockk<AccessibilityNodeInfo>()
    private val window = mockk<AccessibilityWindowInfo>()
    private var currentText = "abcd"
    private var cursorRestored = true
    private val arguments = mutableMapOf<String, Any>()

    @After
    fun tearDown() = unmockkAll()

    private fun focusedField(start: Int = 1, end: Int = 3, hint: Boolean = false) {
        val root = mockk<AccessibilityNodeInfo> {
            every { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } returns node
        }
        every { window.root } returns root
        every { window.id } returns 7
        every { window.isFocused } returns true
        val displays = mockk<SparseArray<List<AccessibilityWindowInfo>>> {
            every { get(10) } returns listOf(window)
        }
        every { service.windowsOnAllDisplays } returns displays
        every { node.refresh() } returns true
        every { node.isFocused } returns true
        every { node.isEditable } returns true
        every { node.isEnabled } returns true
        every { node.isVisibleToUser } returns true
        every { node.isPassword } returns false
        every { node.isShowingHintText } returns hint
        every { node.text } answers { currentText }
        every { node.textSelectionStart } returns start
        every { node.textSelectionEnd } returns end
        every { node.actionList } returns listOf(mockk {
            every { id } returns AccessibilityNodeInfo.ACTION_SET_TEXT
        })
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putCharSequence(any(), any()) } answers {
            arguments[firstArg()] = secondArg<CharSequence>().toString()
        }
        every { anyConstructed<Bundle>().putInt(any(), any()) } answers {
            arguments[firstArg()] = secondArg<Int>()
        }
        every { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) } answers {
            currentText = arguments[AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE] as String
            true
        }
        every { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, any()) } answers { cursorRestored }
    }

    @Test
    fun editsOnlyFocusedSelectionOnTheRequestedDisplay() {
        focusedField()
        assertEquals(MobileTextInputOutcome.INSERTED, service.insertText(10, "新"))
        assertEquals("a新d", currentText)
        assertEquals(2, arguments[AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT])
        assertEquals(2, arguments[AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT])
        verify(exactly = 0) { service.rootInActiveWindow }
        verify(exactly = 1) { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) }
    }

    @Test
    fun targetBoundInputRejectsFocusAndSelectionChangesBeforeWriting() {
        focusedField()
        val root = mockk<AccessibilityNodeInfo> {
            every { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } returns node
            every { getChild(0) } returns node
        }
        every { window.root } returns root
        every { node.viewIdResourceName } returns null
        every { node.className } returns null
        every { node.packageName } returns null
        every { node.getBoundsInScreen(any()) } returns Unit
        val field = MobileElement(
            "field", "accessibility", MobileBounds(0, 0, 0, 0),
            windowId = 7, text = "abcd", focused = true, editable = true, selectionStart = 1, selectionEnd = 3,
        )
        val target = MobileNodeTarget(field, listOf(0), field, null, null)
        every { node.textSelectionStart } returns 0
        assertEquals(MobileTextInputOutcome.REJECTED, service.insertText(10, "new", target))
        every { node.textSelectionStart } returns 1
        every { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } returns mockk(relaxed = true)
        assertEquals(MobileTextInputOutcome.REJECTED, service.insertText(10, "new", target))
        verify(exactly = 0) { node.performAction(any(), any()) }
        every { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } returns node
        assertEquals(MobileTextInputOutcome.INSERTED, service.insertText(10, "new", target))
        verify(exactly = 1) { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) }
    }

    @Test
    fun hintIsNotInsertedAsExistingUserText() {
        focusedField(start = 0, end = 0, hint = true)
        currentText = "请输入搜索内容"
        assertEquals(MobileTextInputOutcome.INSERTED, service.insertText(10, "搜索词"))
        assertEquals("搜索词", currentText)
    }

    @Test
    fun emptyHintFieldWithUnreportedSelectionAcceptsTextOnce() {
        focusedField(start = -1, end = -1, hint = true)
        currentText = "搜索"
        assertEquals(MobileTextInputOutcome.INSERTED, service.insertText(10, "抖音"))
        assertEquals("抖音", currentText)
        verify(exactly = 1) { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) }
    }

    @Test
    fun missingFocusOrPasswordFieldNeverReceivesText() {
        focusedField()
        every { window.isFocused } returns false
        assertEquals(MobileTextInputOutcome.REJECTED, service.insertText(10, "new"))
        every { window.isFocused } returns true
        every { node.isPassword } returns true
        assertEquals(MobileTextInputOutcome.REJECTED, service.insertText(10, "new"))
        verify(exactly = 0) { node.performAction(any(), any()) }
    }

    @Test
    fun failedCursorRestorationReportsPartialSuccessWithoutRepeatingText() {
        focusedField()
        cursorRestored = false
        assertEquals(MobileTextInputOutcome.CURSOR_UNAVAILABLE, service.insertText(10, "new"))
        assertEquals("anewd", currentText)
        verify(exactly = 1) { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) }
    }

    @Test
    fun pastePreservesTheSelectionAndRestoresClipboardAfterConfirmedInput() {
        focusedField()
        every { node.actionList } returns listOf(mockk { every { id } returns AccessibilityNodeInfo.ACTION_PASTE })
        assertTrue(service.requiresPaste(10))
        var pasted = false
        every { node.textSelectionStart } answers { if (pasted) 2 else 1 }
        every { node.textSelectionEnd } answers { if (pasted) 2 else 3 }
        every { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) } answers {
            currentText = "a新d"; pasted = true; true
        }
        var restored = 0
        assertEquals(MobileTextInputOutcome.INSERTED, service.pasteText(10, "新", { true }, { restored++ }))
        assertEquals(1, restored)
        verify(exactly = 1) { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
        verify(exactly = 0) { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) }
    }

    @Test
    fun losingFocusDuringClipboardPreparationRejectsPasteAndRestoresTheOriginal() {
        focusedField()
        every { node.actionList } returns listOf(mockk { every { id } returns AccessibilityNodeInfo.ACTION_PASTE })
        var restored = 0
        val outcome = service.pasteText(10, "新", {
            every { window.isFocused } returns false
            true
        }, { restored++ })
        assertEquals(MobileTextInputOutcome.REJECTED, outcome)
        assertEquals(1, restored)
        verify(exactly = 0) { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
    }

    @Test
    fun unconfirmedPasteIsNotRepeatedOrReplacedWithSetText() {
        focusedField()
        every { node.actionList } returns listOf(mockk { every { id } returns AccessibilityNodeInfo.ACTION_PASTE })
        every { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) } returns true
        mockkStatic(SystemClock::class)
        var clock = 0L
        every { SystemClock.uptimeMillis() } answers { clock += 100; clock }
        every { SystemClock.sleep(any()) } returns Unit
        var restored = 0
        assertEquals(MobileTextInputOutcome.UNCONFIRMED, service.pasteText(10, "新", { true }, { restored++ }))
        assertEquals(1, restored)
        verify(exactly = 1) { node.performAction(AccessibilityNodeInfo.ACTION_PASTE) }
        verify(exactly = 0) { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, any()) }
    }
}
