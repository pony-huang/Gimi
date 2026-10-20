package github.ponyhuang.gimi.data.plugin.remote

import github.ponyhuang.gimi.domain.plugin.model.PluginRelease
import github.ponyhuang.gimi.domain.plugin.model.PluginReleaseAsset
import github.ponyhuang.gimi.domain.plugin.repository.PluginReleaseRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 读取本仓库最新 GitHub Release 的插件资产。
 *
 * 未认证 GitHub API 限流 60 次/小时/IP，因此只在安装时请求并做 10 分钟内存缓存；
 * 失败不缓存，允许用户重试。所有异常向上抛出 IOException，由安装用例统一映射。
 */
@Singleton
class GitHubPluginReleaseRepository internal constructor(
    private val okHttpClient: OkHttpClient,
    private val latestReleaseUrl: String,
) : PluginReleaseRepository {

    @Inject
    constructor(okHttpClient: OkHttpClient) : this(okHttpClient, LATEST_RELEASE_URL)

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cached: Pair<Long, PluginRelease>? = null

    override suspend fun latestRelease(): PluginRelease = mutex.withLock {
        val now = System.currentTimeMillis()
        cached?.takeIf { now - it.first < CACHE_TTL_MS }?.let { return@withLock it.second }
        val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
        cached = now to release
        release
    }

    private fun fetchLatestRelease(): PluginRelease {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 403 || response.code == 429) {
                val remaining = response.header("X-RateLimit-Remaining")
                if (remaining == null || remaining == "0") {
                    throw IOException("GitHub API rate limited")
                }
            }
            if (!response.isSuccessful) throw IOException("GitHub API HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty release body")
            val dto = json.decodeFromString<PluginGitHubReleaseDto>(body)
            if (dto.draft || dto.prerelease) throw IOException("Latest release is not publishable")
            return PluginRelease(
                tagName = dto.tagName,
                assets = dto.assets.mapNotNull(::toAssetOrNull),
            )
        }
    }

    private fun toAssetOrNull(dto: PluginGitHubAssetDto): PluginReleaseAsset? {
        val name = dto.name.trim()
        if (name.isEmpty() || name.length > MAX_ASSET_NAME_LENGTH) return null
        // 只接受 HTTPS 直链，避免降级为明文下载。
        if (!dto.downloadUrl.startsWith("https://", ignoreCase = true)) return null
        if (dto.size < 0) return null
        val sha256 = dto.digest
            ?.takeIf { it.startsWith(SHA256_PREFIX, ignoreCase = true) }
            ?.removePrefix(SHA256_PREFIX)
            ?.takeIf { it.matches(SHA256_PATTERN) }
        return PluginReleaseAsset(
            name = name,
            downloadUrl = dto.downloadUrl,
            sizeBytes = dto.size,
            sha256 = sha256,
        )
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/pony-huang/Gimi/releases/latest"
        const val CACHE_TTL_MS = 10 * 60 * 1000L
        const val MAX_ASSET_NAME_LENGTH = 256
        const val SHA256_PREFIX = "sha256:"
        val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
    }
}
