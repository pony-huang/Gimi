package github.ponyhuang.gimi.data.mobileuse

import github.ponyhuang.gimi.domain.mobileuse.MobileElement

/** 一个可见落点及其实际接收点击的节点，供下一次快照复核。 */
internal data class MobileClickTarget(
    val node: MobileNodeTarget,
    val label: String,
    val context: String?,
) {
    val bounds get() = node.element.bounds
}

internal fun MobileAccessibilitySnapshot.clickTargets(): List<MobileClickTarget> {
    if (!consistent || status != "available") return emptyList()
    // 整行节点有时汇总子控件的文本，含敏感子节点的容器也不提供文本。
    val sensitiveContainers = targets.values.filter { it.sensitive || it.ownElement.editable }.flatMap { node ->
        node.path.indices.map { index -> node.element.windowId to node.path.take(index) }
    }.toSet()
    fun safeLabel(element: MobileElement, path: List<Int>): String? =
        if ((element.windowId to path) in sensitiveContainers) null else element.safeLabel()
    val byPath = targets.values.groupBy { it.element.windowId to it.path.dropLast(1) }
    return targets.values.mapNotNull { node ->
        val receiver = node.clickElement ?: return@mapNotNull null
        val clickPath = node.clickPath ?: return@mapNotNull null
        val element = node.ownElement
        if (node.sensitive || !element.enabled || !receiver.enabled ||
            !receiver.bounds.contains(element.bounds.centerX, element.bounds.centerY) ||
            windows.covers(element)
        ) return@mapNotNull null
        val label = safeLabel(element, node.path) ?: safeLabel(receiver, clickPath) ?: return@mapNotNull null
        val context = listOf(node.path, clickPath)
            .flatMap { path -> listOf(path.dropLast(1), path.dropLast(2)) }
            .distinct()
            .asSequence()
            .filter { it.isNotEmpty() }
            .flatMap { path -> byPath[element.windowId to path].orEmpty().asSequence() }
            .filter { sibling -> !sibling.sensitive && sibling.path != node.path && !sibling.ownElement.scrollable }
            .mapNotNull { safeLabel(it.ownElement, it.path) }
            .firstOrNull { it != label }
        MobileClickTarget(node, label, context)
    }
}

private fun MobileElement.safeLabel(): String? =
    (if (editable) description?.takeUnless { it == text } else text ?: description)?.trim()?.take(80)?.takeIf { it.isNotBlank() }
