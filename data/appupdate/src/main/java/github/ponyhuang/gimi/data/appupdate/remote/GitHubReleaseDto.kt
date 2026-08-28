package github.ponyhuang.gimi.data.appupdate.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("assets") val assets: List<GitHubAssetDto>? = null,
)

@Serializable
internal data class GitHubAssetDto(
    @SerialName("name") val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("size") val size: Long,
    /** 形如 "sha256:abcd..."，GitHub 2024 起提供，可能缺失。 */
    @SerialName("digest") val digest: String? = null,
)
