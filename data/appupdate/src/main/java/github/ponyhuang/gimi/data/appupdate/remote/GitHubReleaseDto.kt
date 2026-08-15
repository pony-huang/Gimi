package github.ponyhuang.gimi.data.appupdate.remote

import com.google.gson.annotations.SerializedName

internal data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("draft") val draft: Boolean,
    @SerializedName("prerelease") val prerelease: Boolean,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("assets") val assets: List<GitHubAssetDto>?,
)

internal data class GitHubAssetDto(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long,
    /** 形如 "sha256:abcd..."，GitHub 2024 起提供，可能缺失。 */
    @SerializedName("digest") val digest: String?,
)
