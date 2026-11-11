package github.ponyhuang.gimi.data.plugin.catalog

import github.ponyhuang.gimi.domain.plugin.model.LocalizedText
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * 解析插件订阅目录并做最小结构校验。
 *
 * 订阅文件可能来自用户配置的远端地址，因此先按 JsonElement 解码再逐条转换：
 * 单条结构损坏或字段非法只跳过该条，不让整个目录不可用；重复 key 只保留首次出现项。
 */
class PluginCatalogParser @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): List<PluginCatalogEntry> {
        // 先解析为 JsonElement，再逐条解码 entries；任何一条失败（缺字段/类型不符）只丢弃该条。
        val entriesArray = runCatching {
            val root = json.parseToJsonElement(content)
            if (root is JsonObject) {
                root["entries"]?.jsonArray ?: JsonArray(emptyList())
            } else {
                root.jsonArray
            }
        }.getOrElse { return emptyList() }
        val seenKeys = mutableSetOf<String>()
        return entriesArray.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement<PluginCatalogEntryDto>(element) }.getOrNull()
        }
            .mapNotNull { it.toDomainOrNull() }
            .filter { seenKeys.add(it.key) }
            .sortedWith(compareBy({ it.sortOrder }, { it.title }))
    }

    private fun PluginCatalogEntryDto.toDomainOrNull(): PluginCatalogEntry? {
        val normalizedKey = key.trim()
        val normalizedPackage = packageName.trim()
        val normalizedTitle = title.trim()
        if (normalizedKey.isEmpty() || normalizedTitle.isEmpty()) return null
        if (!normalizedPackage.matches(PACKAGE_NAME_PATTERN)) return null
        if (pluginVersion <= 0) return null
        if (versionCode != null && versionCode <= 0L) return null

        val directUrl = apkUrl?.trim()?.takeIf(String::isNotEmpty)
        if (directUrl != null &&
            (!directUrl.startsWith("https://", ignoreCase = true) ||
                directUrl.length > MAX_URL_LENGTH)
        ) {
            return null
        }
        val normalizedPrefix = releaseAssetPrefix.trim()
        if (directUrl == null) {
            // 无直链时必须能从最新 Release 按前缀解析。
            if (!normalizedPrefix.startsWith(RELEASE_ASSET_PREFIX_PREFIX) ||
                !normalizedPrefix.endsWith(RELEASE_ASSET_PREFIX_SUFFIX)
            ) {
                return null
            }
        }
        val normalizedSha = sha256?.trim()?.takeIf { it.matches(SHA256_PATTERN) }
        val normalizedSize = sizeBytes?.takeIf { it > 0L && it <= MAX_APK_BYTES }
        return PluginCatalogEntry(
            key = normalizedKey,
            packageName = normalizedPackage,
            title = normalizedTitle,
            summary = summary.toDomain(),
            description = description.toDomain(),
            highlights = highlights.map { it.toDomain() }.filterNot { it.isBlankText() },
            sortOrder = sortOrder,
            pluginVersion = pluginVersion,
            releaseAssetPrefix = normalizedPrefix,
            repoUrl = repoUrl.trim(),
            iconKey = iconKey.trim().ifBlank { normalizedKey },
            tags = tags.map(String::trim).filter(String::isNotEmpty),
            apkUrl = directUrl,
            sha256 = normalizedSha,
            sizeBytes = normalizedSize,
            versionCode = versionCode,
            versionName = versionName?.trim()?.takeIf(String::isNotEmpty),
        )
    }

    private fun LocalizedTextDto.toDomain() = LocalizedText(zh = zh.trim(), en = en.trim())

    private fun LocalizedText.isBlankText(): Boolean = zh.isBlank() && en.isBlank()

    private companion object {
        val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_.]+")
        val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
        const val RELEASE_ASSET_PREFIX_PREFIX = "Gimi-plugin-"
        const val RELEASE_ASSET_PREFIX_SUFFIX = "-v"
        const val MAX_URL_LENGTH = 2_048
        const val MAX_APK_BYTES = 50L * 1024L * 1024L
    }
}
