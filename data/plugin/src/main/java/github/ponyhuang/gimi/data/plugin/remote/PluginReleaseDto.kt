package github.ponyhuang.gimi.data.plugin.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GitHub Releases API 的最小响应投影；忽略插件安装不需要的字段。 */
@Serializable
internal data class PluginGitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("assets") val assets: List<PluginGitHubAssetDto> = emptyList(),
)

/** 单个 Release 资产；[digest] 形如 `sha256:abcd...`，旧资产可能缺失。 */
@Serializable
internal data class PluginGitHubAssetDto(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long = 0L,
    @SerialName("digest") val digest: String? = null,
)
