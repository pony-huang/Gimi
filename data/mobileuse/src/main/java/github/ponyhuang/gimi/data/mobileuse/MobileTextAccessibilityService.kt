package github.ponyhuang.gimi.data.mobileuse

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** 仅向指定副屏上的可编辑节点写入文本，不请求焦点或唤起系统输入法。 */
class MobileTextAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = this
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    internal fun replaceText(displayId: Int, x: Int, y: Int, text: String): Boolean {
        val windows = runCatching { windowsOnAllDisplays[displayId] }.getOrNull() ?: return false
        for (window in windows) {
            val root = window.root ?: continue
            val target = findEditable(root, x, y) ?: continue
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            return runCatching {
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }.getOrDefault(false)
        }
        return false
    }

    private fun findEditable(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.contains(x, y)) return null
        for (index in node.childCount - 1 downTo 0) {
            val child = node.getChild(index) ?: continue
            findEditable(child, x, y)?.let { return it }
        }
        val canSetText = node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
        return node.takeIf { it.isEditable && canSetText }
    }

    companion object {
        @Volatile private var connected: MobileTextAccessibilityService? = null

        internal fun current(): MobileTextAccessibilityService? = connected
    }
}
