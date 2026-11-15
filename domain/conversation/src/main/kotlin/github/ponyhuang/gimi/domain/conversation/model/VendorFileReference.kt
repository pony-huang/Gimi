package github.ponyhuang.gimi.domain.conversation.model

/**
 * 厂商文件引用的私有 URI 约定：`gimi-file://<vendor>/<fileId>`。
 *
 * 会话与 ADK `FileData` 始终携带该引用（而不是本地路径），历史重放由对应厂商适配器
 * 直通转换为协议的 file 块——重放不读本地文件、不上传、不做内容哈希，fileId 是唯一真源。
 * 其他厂商适配器收到该 scheme 按"不支持的媒体"降级，天然隔离。
 *
 * [vendor] 使用厂商模板 ID（如 `deepseek`）；fileId 由厂商 Files API 签发，本地不缓存。
 */
object VendorFileReference {
    const val SCHEME: String = "gimi-file"

    /** 组装会话内传递的私有引用 URI。 */
    fun uri(vendor: String, fileId: String): String = "$SCHEME://$vendor/$fileId"

    /** 解析私有引用；非该 scheme 或缺段时返回 null。 */
    fun parse(uri: String): Pair<String, String>? {
        val prefix = "$SCHEME://"
        if (!uri.startsWith(prefix)) return null
        val rest = uri.removePrefix(prefix)
        val vendor = rest.substringBefore('/')
        val fileId = rest.substringAfter('/', missingDelimiterValue = "")
        if (vendor.isEmpty() || fileId.isEmpty()) return null
        return vendor to fileId
    }
}
