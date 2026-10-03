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

    internal fun snapshot(displayId: Int, width: Int, height: Int): MobileAccessibilitySnapshot {
        return try {
            val windows = windowsOnAllDisplays[displayId].orEmpty().sortedByDescending { it.layer }
            val states = windows.map { window ->
                MobileWindowState(window.id, window.layer, Rect().also(window::getBoundsInScreen).domainBounds())
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
                    (own.text != null || own.description != null || own.actions.isNotEmpty() || own.scrollable)
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
            // 内容事件可能来自计时器或广告，只有窗口集合与几何变化使整次遍历失效。
            // 具体目标的内容与位置由动作前的快照及 performNodeAction 再次核对。
            val currentWindows = windowsOnAllDisplays[displayId].orEmpty().sortedByDescending { it.layer }.map { window ->
                MobileWindowState(window.id, window.layer, Rect().also(window::getBoundsInScreen).domainBounds())
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

    internal fun performNodeAction(
        displayId: Int, width: Int, height: Int, target: MobileNodeTarget, action: String, text: String? = null,
    ): Boolean {
        val window = windowsOnAllDisplays[displayId].orEmpty().firstOrNull { it.id == target.element.windowId }
            ?: return false
        val state = MobileWindowState(window.id, window.layer, Rect().also(window::getBoundsInScreen).domainBounds())
        // 先复核标签节点，再复核真正接收动作的父节点，避免旧路径指向新出现的内容。
        val label = nodeAt(window.root, target.path) ?: return false
        if (!label.refresh() || label.element(state, target.path, width, height) != target.ownElement) return false
        val path = if (action == "click") target.clickPath ?: return false else target.path
        val expected = if (action == "click") target.clickElement else target.ownElement
        val node = nodeAt(window.root, path) ?: return false
        if (!node.refresh() || node.element(state, path, width, height) != expected) return false
        val actionId = if (action == "click") AccessibilityNodeInfo.ACTION_CLICK else AccessibilityNodeInfo.ACTION_SET_TEXT
        val arguments = if (action == "set_text") Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        } else null
        return node.performAction(actionId, arguments)
    }

    internal fun replaceText(displayId: Int, width: Int, height: Int, x: Int, y: Int, text: String): Boolean {
        val current = snapshot(displayId, width, height)
        val target = current.targets.values.lastOrNull {
            "set_text" in it.element.actions && it.element.bounds.contains(x, y)
        } ?: return false
        return performNodeAction(displayId, width, height, target, "set_text", text)
    }

    private fun nodeAt(root: AccessibilityNodeInfo?, path: List<Int>): AccessibilityNodeInfo? {
        var node = root ?: return null
        for (index in path) node = node.getChild(index) ?: return null
        return node
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
