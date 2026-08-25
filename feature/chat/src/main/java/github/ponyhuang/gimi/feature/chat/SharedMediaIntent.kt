package github.ponyhuang.gimi.feature.chat

import android.content.Intent
import android.net.Uri

/**
 * 从系统分享 intent 中提取图片 URI。
 *
 * 仅接受 [Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE] 且 MIME 类型为
 * 图片类型的分享。同一张图片可能同时出现在 clip data 与 extra stream 中，这里
 * 按字符串去重并保留原始顺序，最多返回 [maxItems] 项。
 */
public fun sharedImageUris(intent: Intent, maxItems: Int = 3): List<Uri> {
    if (
        intent.action != Intent.ACTION_SEND &&
        intent.action != Intent.ACTION_SEND_MULTIPLE
    ) {
        return emptyList()
    }
    if (intent.type?.startsWith("image/") != true) return emptyList()

    val candidates = buildList {
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { uri -> add(uri.toString()) }
            }
        }
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            ?.let { uri -> add(uri.toString()) }
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            ?.forEach { uri -> add(uri.toString()) }
    }

    return dedupeAndLimitUris(candidates, maxItems).map(Uri::parse)
}

/**
 * 对 URI 字符串做 trim、去空白、去重并截断到最多 [maxItems] 项。
 */
internal fun dedupeAndLimitUris(
    uris: List<String>,
    maxItems: Int = 3,
): List<String> = uris.asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .take(maxItems)
    .toList()
