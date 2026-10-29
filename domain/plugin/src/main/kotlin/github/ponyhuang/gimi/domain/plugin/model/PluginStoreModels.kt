package github.ponyhuang.gimi.domain.plugin.model

/**
 * 商店条目的一段本地化文案。
 *
 * @property zh 中文文案，默认语言。
 * @property en 英文文案；缺失时回退到中文。
 */
data class LocalizedText(
    val zh: String,
    val en: String,
) {
    /** 按语言标签选择文案；非中文一律使用英文，英文为空时回退中文。 */
    fun resolve(languageTag: String): String =
        if (languageTag.lowercase().startsWith("zh")) {
            zh.ifBlank { en }
        } else {
            en.ifBlank { zh }
        }
}

/**
 * 一个可安装的插件商店条目。
 *
 * @property key 稳定标识，同时对应插件运行时 `pluginId`。
 * @property packageName 插件 APK 的 Android 包名，用于安装/卸载。
 * @property title 品牌名称，通常不随语言变化。
 * @property summary 列表卡片上的短描述。
 * @property description 详情页描述。
 * @property highlights 详情页能力要点。
 * @property sortOrder 目录内的展示顺序，数值越小越靠前。
 * @property pluginVersion 插件自身行为版本，不用于 APK 更新判断。
 * @property versionCode 发布 APK 的安装版本；缺失时不提示更新。
 * @property versionName 发布 APK 的展示版本。
 * @property releaseAssetPrefix GitHub Release 资产名前缀，如 `Gimi-plugin-zhihu-v`。
 * @property repoUrl 插件所属仓库地址，仅用于详情页外链。
 * @property iconKey 宿主内置图标映射键，不依赖网络图片。
 * @property tags 本地搜索关键词。
 * @property apkUrl 订阅源直接给出的 APK 下载地址；为空时按 [releaseAssetPrefix] 从最新 Release 解析。
 * @property sha256 直链 APK 的 SHA-256；为空时跳过摘要校验。
 * @property sizeBytes 直链 APK 的字节数，用于下载上限与进度提示。
 */
data class PluginCatalogEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val summary: LocalizedText,
    val description: LocalizedText,
    val highlights: List<LocalizedText>,
    val sortOrder: Int,
    val pluginVersion: Int,
    val releaseAssetPrefix: String,
    val repoUrl: String,
    val iconKey: String,
    val tags: List<String>,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
)

/** 订阅目录来源；远端失败时回退到内置目录。 */
enum class PluginCatalogSource {
    REMOTE,
    BUNDLED,
}

/**
 * 一次目录读取结果。
 *
 * @property entries 合并后的可用条目。
 * @property source 实际生效的来源。
 * @property error 至少一个远端订阅源读取失败。
 */
data class PluginCatalogLoad(
    val entries: List<PluginCatalogEntry>,
    val source: PluginCatalogSource,
    val error: Boolean = false,
)

/** 一次 GitHub Release 中与插件安装相关的快照。 */
data class PluginRelease(
    val tagName: String,
    val assets: List<PluginReleaseAsset>,
)

/**
 * 一个可下载的插件 APK 资产。
 *
 * @property name 资产文件名。
 * @property downloadUrl GitHub 提供的下载地址。
 * @property sizeBytes 资产字节数，用于下载上限与进度提示。
 * @property sha256 GitHub asset digest 中的 SHA-256；旧资产可能缺失。
 */
data class PluginReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
)

/** 插件安装的可本地化失败原因；不携带 URL、路径或凭据。 */
enum class PluginInstallFailure {
    NETWORK,
    CHECKSUM_MISMATCH,
    NO_RELEASE_ASSET,
    PACKAGE_TOO_LARGE,
    STORAGE,
    INVALID_PACKAGE,
    NOT_AN_UPDATE,
    UNKNOWN,
}

/** 安装流程结果；成功时携带待交给系统安装器的本地 APK 路径。 */
sealed interface PluginInstallResult {
    /** 下载与校验完成；[apkPath] 为受管缓存里的 APK。 */
    data class Ready(val apkPath: String) : PluginInstallResult

    /** 准备安装失败；[failure] 供 UI 本地化展示。 */
    data class Failed(val failure: PluginInstallFailure) : PluginInstallResult
}

/** data 层下载/校验失败的领域异常，安装用例据此映射为 [PluginInstallFailure]。 */
class PluginPackageInstallException(
    val failure: PluginInstallFailure,
) : Exception(failure.name)

/**
 * 在 Release 资产中按目录前缀选择插件 APK。
 * 只接受 `.apk` 后缀；同前缀多个资产时保持 Release 顺序取第一个。
 */
fun PluginRelease.selectPluginAsset(prefix: String): PluginReleaseAsset? =
    assets.firstOrNull { asset ->
        asset.name.startsWith(prefix) && asset.name.endsWith(".apk", ignoreCase = true)
    }

/** 仅同一 Android 包的更高安装版本可覆盖更新；插件行为版本与版本名称不参与比较。 */
fun PluginCatalogEntry.isUpdateFor(installed: PluginDescriptor): Boolean =
    packageName == installed.packageName && installed.versionCode > 0L &&
        versionCode != null && versionCode > installed.versionCode
