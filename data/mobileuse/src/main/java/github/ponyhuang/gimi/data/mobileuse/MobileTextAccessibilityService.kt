package github.ponyhuang.gimi.data.mobileuse

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement
import java.util.concurrent.ConcurrentHashMap

/** 仅查询指定后台显示的节点并投递节点动作，不使用主屏活动窗口或唤起系统输入法。 */
class MobileTextAccessibilityService : AccessibilityService() {
    private val changes = ConcurrentHashMap<Int, DisplayChange>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try {
            // displayId 直接定位事件所属显示（API 33+，minSdk 34 必有），避免每条事件
            // 全量拉取并遍历所有显示器的窗口列表；取不到时再按窗口归属回退匹配。
            val displayId = event.displayId
            if (displayId >= 0) {
                changes.compute(displayId) { _, previous ->
                    DisplayChange((previous?.revision ?: 0) + 1, SystemClock.elapsedRealtime())
                }
                return
            }
            val displays = windowsOnAllDisplays
            for (index in 0 until displays.size()) {
                val id = displays.keyAt(index)
                if (displays.valueAt(index).any { it.id == event.windowId }) {
                    changes.compute(id) { _, previous ->
                        DisplayChange((previous?.revision ?: 0) + 1, SystemClock.elapsedRealtime())
                    }
                    break
                }
            }
        } catch (failure: RuntimeException) {
            Log.w("GimiMobileUse", "Unable to associate accessibility event with display", failure)
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        changes.clear()
        super.onDestroy()
    }

    internal fun forgetDisplay(displayId: Int) { changes.remove(displayId) }
    internal fun lastChangeMs(displayId: Int): Long = changes[displayId]?.atMs ?: 0

    /** WebView 编辑器依赖真实粘贴产生输入事件；只检查指定副屏当前已聚焦的节点。 */
    internal fun requiresPaste(displayId: Int, expectedTarget: MobileNodeTarget? = null): Boolean {
        var node = focusedEditable(displayId, expectedTarget) ?: return false
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } &&
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_PASTE }) return true
        repeat(24) {
            if (node.className?.toString() == "android.webkit.WebView") return true
            node = node.parent ?: return false
        }
        return false
    }

    /** 粘贴只执行一次；异步读回不能确认时报告未知，不能再用 SET_TEXT 补写相同内容。 */
    internal fun pasteText(
        displayId: Int, text: String, prepare: () -> Boolean, restore: () -> Unit,
        expectedTarget: MobileNodeTarget? = null,
    ): MobileTextInputOutcome {
        val node = focusedEditable(displayId, expectedTarget) ?: return MobileTextInputOutcome.REJECTED
        val original = if (node.isShowingHintText) "" else node.text?.toString()
        val insertion = MobileTextInsertion.plan(original, node.textSelectionStart, node.textSelectionEnd, text)
            ?: return MobileTextInputOutcome.REJECTED
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_PASTE }) return MobileTextInputOutcome.REJECTED
        try {
            if (!prepare()) return MobileTextInputOutcome.REJECTED
            // 保存剪贴板要跨 Binder；回来后目标与选区必须仍是最初那个，不能粘进新焦点。
            if (!node.refresh() || focusedEditable(displayId, expectedTarget) != node ||
                (if (node.isShowingHintText) "" else node.text?.toString()) != original) return MobileTextInputOutcome.REJECTED
            val currentPlan = MobileTextInsertion.plan(original, node.textSelectionStart, node.textSelectionEnd, text)
            if (currentPlan != insertion) return MobileTextInputOutcome.REJECTED
            if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return MobileTextInputOutcome.REJECTED
            val deadline = SystemClock.uptimeMillis() + 750
            do {
                if (node.refresh() && focusedEditable(displayId) == node && node.text?.toString() == insertion.text) {
                    return if (node.textSelectionStart == insertion.cursor && node.textSelectionEnd == insertion.cursor)
                        MobileTextInputOutcome.INSERTED else MobileTextInputOutcome.CURSOR_UNAVAILABLE
                }
                SystemClock.sleep(25)
            } while (SystemClock.uptimeMillis() < deadline)
            return MobileTextInputOutcome.UNCONFIRMED
        } finally {
            restore()
        }
    }

    private fun focusedEditable(displayId: Int, expectedTarget: MobileNodeTarget? = null): AccessibilityNodeInfo? {
        val window = windowsOnAllDisplays[displayId].orEmpty().singleOrNull { it.isFocused } ?: return null
        val node = window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (!node.refresh() || !node.isFocused || !node.isEditable || !node.isEnabled ||
            !node.isVisibleToUser || node.isPassword
        ) return null
        if (expectedTarget != null) {
            val expected = expectedTarget.ownElement
            var expectedNode = window.root
            for (index in expectedTarget.path) expectedNode = expectedNode?.getChild(index)
            // guard 复核到真正写入之间仍可能切换焦点，不能重新选中后来出现的编辑框。
            if (window.id != expected.windowId || expectedNode != node ||
                node.viewIdResourceName != expected.resourceId || node.className?.toString() != expected.className ||
                node.packageName?.toString() != expected.packageName ||
                node.text?.toString()?.takeIf { it.isNotBlank() }?.take(500) != expected.text ||
                node.textSelectionStart != expected.selectionStart || node.textSelectionEnd != expected.selectionEnd ||
                Rect().also(node::getBoundsInScreen).domainBounds() != expected.bounds
            ) return null
        }
        return node
    }

    internal fun snapshot(displayId: Int, width: Int, height: Int): MobileAccessibilitySnapshot {
        return try {
            val windows = windowsOnAllDisplays[displayId].orEmpty().sortedByDescending { it.layer }
            val states = windows.map { window ->
                MobileWindowState(window.id, window.layer, Rect().also(window::getBoundsInScreen).domainBounds(), window.isFocused)
            }
            val targets = linkedMapOf<String, MobileNodeTarget>()
            var visited = 0
            var truncated = false
            val deadline = SystemClock.elapsedRealtime() + 500
            fun walk(node: AccessibilityNodeInfo, path: List<Int>, window: MobileWindowState,
                     clickAncestor: Pair<List<Int>, MobileElement>?) {
                if (++visited > 2_000 || path.size > 48 || targets.size >= 200 || SystemClock.elapsedRealtime() >= deadline) {
                    truncated = true
                    return
                }
                val own = node.element(window, path, width, height)
                val click = if ("click" in own.actions) path to own else clickAncestor
                if (node.isVisibleToUser && own.bounds.right > own.bounds.left &&
                    own.bounds.bottom > own.bounds.top && !states.covers(own) &&
                    (own.text != null || own.description != null || own.actions.isNotEmpty() || own.scrollable || (own.focused && own.editable))
                ) {
                    val actions = own.actions.toMutableList()
                    if (own.enabled && click != null && "click" !in actions) actions += "click"
                    val element = own.copy(actions = actions)
                    targets[element.id] = MobileNodeTarget(element, path, own, click?.first, click?.second)
                }
                for (index in 0 until node.childCount.coerceAtMost(2_000)) {
                    if (visited >= 2_000 || targets.size >= 200 || SystemClock.elapsedRealtime() >= deadline) { truncated = true; break }
                    node.getChild(index)?.let { walk(it, path + index, window, click) }
                }
            }
            for (window in windows) {
                val root = window.root ?: continue
                if (!root.refresh()) continue
                walk(root, emptyList(), states.first { it.id == window.id }, null)
                if (visited >= 2_000 || targets.size >= 200 || SystemClock.elapsedRealtime() >= deadline) break
            }
            val after = changes[displayId]
            // 内容事件可能来自计时器或广告，只有窗口集合、几何或输入焦点变化使整次遍历失效。
            // 具体目标的内容、位置和焦点由动作前的快照再次核对。
            val currentWindows = windowsOnAllDisplays[displayId].orEmpty().sortedByDescending { it.layer }.map { window ->
                MobileWindowState(window.id, window.layer, Rect().also(window::getBoundsInScreen).domainBounds(), window.isFocused)
            }
            MobileAccessibilitySnapshot(
                status = if (windows.isEmpty()) "no_windows" else "available",
                revision = after?.revision ?: 0, lastChangeMs = after?.atMs ?: 0,
                windows = states, targets = targets, truncated = truncated, consistent = states == currentWindows,
            )
        } catch (failure: RuntimeException) {
            Log.w("GimiMobileUse", "Unable to retrieve display nodes", failure)
            val change = changes[displayId]
            MobileAccessibilitySnapshot("accessibility_error", revision = change?.revision ?: 0, lastChangeMs = change?.atMs ?: 0)
        }
    }

    internal fun insertText(displayId: Int, text: String, expectedTarget: MobileNodeTarget? = null): MobileTextInputOutcome {
        // 严格限制在目标显示的输入焦点，不使用主屏 rootInActiveWindow 或猜测坐标。
        val window = windowsOnAllDisplays[displayId].orEmpty().singleOrNull { it.isFocused }
            ?: return MobileTextInputOutcome.REJECTED
        val node = focusedEditable(displayId, expectedTarget)
            ?: return MobileTextInputOutcome.REJECTED
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return MobileTextInputOutcome.REJECTED
        // 空输入框可能把 hint 放在 text 中，提示文字不能被当作用户已输入的内容保留下来。
        val currentText = when {
            node.isShowingHintText -> ""
            node.text != null -> node.text.toString()
            node.textSelectionStart == 0 && node.textSelectionEnd == 0 -> ""
            else -> null
        }
        val insertion = MobileTextInsertion.plan(currentText, node.textSelectionStart, node.textSelectionEnd, text)
            ?: return MobileTextInputOutcome.REJECTED
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, insertion.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return MobileTextInputOutcome.REJECTED
        // 写入与恢复选区是两次动作；中途焦点丢失只报告部分成功，不能重输或补发 Enter。
        if (!node.refresh() || !node.isFocused || node.text?.toString() != insertion.text ||
            windowsOnAllDisplays[displayId].orEmpty().none { it.id == window.id && it.isFocused }
        ) return MobileTextInputOutcome.CURSOR_UNAVAILABLE
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, insertion.cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, insertion.cursor)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)) {
            MobileTextInputOutcome.INSERTED
        } else MobileTextInputOutcome.CURSOR_UNAVAILABLE
    }

    private fun AccessibilityNodeInfo.element(
        window: MobileWindowState, path: List<Int>, width: Int, height: Int,
    ): MobileElement {
        val bounds = Rect().also(::getBoundsInScreen)
        val supported = actionList.map { it.id }
        return MobileElement(
            id = elementId(window.id, path), source = "accessibility",
            bounds = MobileBounds(bounds.left.coerceIn(0, width), bounds.top.coerceIn(0, height),
                bounds.right.coerceIn(0, width), bounds.bottom.coerceIn(0, height)),
            text = text?.toString()?.takeIf { it.isNotBlank() }?.take(500),
            description = contentDescription?.toString()?.takeIf { it.isNotBlank() }?.take(500),
            resourceId = viewIdResourceName, className = className?.toString(), packageName = packageName?.toString(),
            windowId = window.id, windowLayer = window.layer,
            parentId = path.takeIf { it.isNotEmpty() }?.let { elementId(window.id, it.dropLast(1)) },
            clickable = isClickable, editable = isEditable, scrollable = isScrollable, enabled = isEnabled,
            focused = isFocused, selectionStart = textSelectionStart, selectionEnd = textSelectionEnd,
            actions = buildList {
                if (isEnabled && isVisibleToUser && AccessibilityNodeInfo.ACTION_CLICK in supported) add("click")
                if (isEnabled && isVisibleToUser && isEditable && AccessibilityNodeInfo.ACTION_SET_TEXT in supported) add("set_text")
            },
        )
    }

    private fun Rect.domainBounds() = MobileBounds(left, top, right, bottom)
    private fun elementId(windowId: Int, path: List<Int>) = "a${windowId}_${path.joinToString("_")}"

    /** 指定显示的最近一次变化；主屏事件不能使后台观察一直等待。 */
    private data class DisplayChange(val revision: Long, val atMs: Long)

    companion object {
        @Volatile private var connected: MobileTextAccessibilityService? = null
        internal fun current(): MobileTextAccessibilityService? = connected
    }
}
