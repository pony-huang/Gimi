package github.ponyhuang.gimi.domain.conversation.model

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * A remote image exposed by a structured tool response.
 *
 * @property url HTTPS URL used to load the image.
 * @property width Source width in pixels, or zero when unavailable.
 * @property height Source height in pixels, or zero when unavailable.
 */
@Serializable
data class RemoteImageReference(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * Ordered remote images discovered in a structured tool response.
 *
 * @property images Valid, deduplicated images in source order.
 */
@Serializable
data class RemoteImageResult(
    val images: List<RemoteImageReference>,
)

/** Extracts a UI-safe remote image collection from a JSON-native tool response. */
fun parseRemoteImageResult(response: Map<String, Any?>): RemoteImageResult? {
    val rawImages = response.findImageList() ?: return null
    val seenUrls = mutableSetOf<String>()
    val images = rawImages.mapNotNull { raw ->
        val fields = raw as? Map<*, *> ?: return@mapNotNull null
        val rawUrl = fields.string("urlDefault")
            ?: fields.string("url")
            ?: fields.defaultInfoUrl()
            ?: fields.string("urlPre")
            ?: return@mapNotNull null
        val url = rawUrl.toRenderableImageUrl() ?: return@mapNotNull null
        if (!seenUrls.add(url)) return@mapNotNull null
        RemoteImageReference(
            url = url,
            width = (fields["width"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
            height = (fields["height"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        )
    }
    return images.takeIf(List<*>::isNotEmpty)?.let(::RemoteImageResult)
}

private fun Map<*, *>.findImageList(): List<*>? {
    (this["imageList"] as? List<*>)?.let { return it }
    values.forEach { value ->
        val nested = value as? Map<*, *> ?: return@forEach
        nested.findImageList()?.let { return it }
    }
    return null
}

private fun Map<*, *>.defaultInfoUrl(): String? {
    val entries = this["infoList"] as? List<*> ?: return null
    val maps = entries.mapNotNull { it as? Map<*, *> }
    return maps.firstOrNull { it["imageScene"] == "WB_DFT" }?.string("url")
        ?: maps.firstNotNullOfOrNull { it.string("url") }
}

private fun Map<*, *>.string(key: String): String? =
    (this[key] as? String)?.takeIf(String::isNotBlank)

private fun String.toRenderableImageUrl(): String? {
    val parsed = runCatching { URI(this) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (parsed.host.isNullOrBlank()) return null
    // Android 默认拒绝明文网络图片；同一资源优先使用其 HTTPS 地址。
    return if (scheme == "http") replaceFirst("http://", "https://") else this
}
