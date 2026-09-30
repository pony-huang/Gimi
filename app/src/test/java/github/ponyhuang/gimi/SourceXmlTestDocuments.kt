package github.ponyhuang.gimi

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** 读取源 XML 的元素与命名空间属性；注释不能满足配置契约。 */
internal fun sourceXml(path: String): Document = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
}.newDocumentBuilder().parse(File(path))

internal fun Document.elements(tag: String): List<Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }
}

internal fun Element.androidAttribute(name: String): String =
    getAttributeNS("http://schemas.android.com/apk/res/android", name)
