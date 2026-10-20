package github.ponyhuang.gimi.data.plugin.catalog

import kotlinx.serialization.Serializable

/** 插件订阅文件/内置目录的根结构。 */
@Serializable
internal data class PluginCatalogDto(
    val schemaVersion: Int = 1,
    val entries: List<PluginCatalogEntryDto> = emptyList(),
)

/** 单个插件条目的持久化形态；字段语义见 domain 的 PluginCatalogEntry。 */
@Serializable
internal data class PluginCatalogEntryDto(
    val key: String,
    val packageName: String,
    val title: String,
    val summary: LocalizedTextDto,
    val description: LocalizedTextDto,
    val highlights: List<LocalizedTextDto> = emptyList(),
    val sortOrder: Int = 0,
    val pluginVersion: Int = 1,
    /** 内置官方源使用：从最新 GitHub Release 按前缀解析 APK。 */
    val releaseAssetPrefix: String = "",
    val repoUrl: String = "",
    val iconKey: String = "",
    val tags: List<String> = emptyList(),
    /** 自定义订阅源可直接给出 APK 直链与校验信息。 */
    val apkUrl: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
)

/** 中英文案对；缺失字段使用空串，由 domain 的 LocalizedText 做回退。 */
@Serializable
internal data class LocalizedTextDto(
    val zh: String = "",
    val en: String = "",
)
