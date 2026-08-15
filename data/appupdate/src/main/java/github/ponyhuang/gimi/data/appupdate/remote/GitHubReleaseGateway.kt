package github.ponyhuang.gimi.data.appupdate.remote

import com.google.gson.Gson
import java.io.IOException
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
    private val gson = Gson()

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
            return gson.fromJson(body, GitHubReleaseDto::class.java)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/pony-huang/Gimi/releases/latest"
    }
}
