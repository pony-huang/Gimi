package github.ponyhuang.gimi.data.appupdate.remote

import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

internal class RateLimitException : IOException("GitHub API rate limited")

/**
 * GitHub Releases API 网关。未认证请求限流 60 次/小时/IP，
 * 调用方需自行节流（见 DefaultAppUpdateRepository）。
 */
internal class GitHubReleaseGateway(
    private val okHttpClient: OkHttpClient,
    private val latestReleaseUrl: String = LATEST_RELEASE_URL,
) {
    // GitHub 响应含大量未声明的顶层/嵌套字段，Gson 静默忽略、kotlinx 默认抛异常，这里显式忽略。
    private val json = Json { ignoreUnknownKeys = true }

    /** 获取最新正式版 release。 */
    fun fetchLatestRelease(): GitHubReleaseDto {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 429) {
                val remaining = response.header("X-RateLimit-Remaining")
                if (remaining == null || remaining == "0") throw RateLimitException()
            }
            if (!response.isSuccessful) throw IOException("GitHub API HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty response body")
            return json.decodeFromString<GitHubReleaseDto>(body)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/pony-huang/Gimi/releases/latest"
    }
}
