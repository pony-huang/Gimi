package github.ponyhuang.gimi.data.plugin.remote

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GitHubPluginReleaseRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GitHubPluginReleaseRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = GitHubPluginReleaseRepository(
            OkHttpClient(),
            server.url("/releases/latest").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mapsAssetsAndNormalizesDigest() = runTest {
        val sha = "a".repeat(64)
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "tag_name": "v0.13.0",
                  "assets": [
                    {
                      "name": "Gimi-plugin-zhihu-v0.13.0.apk",
                      "browser_download_url": "https://example.com/zhihu.apk",
                      "size": 12345,
                      "digest": "sha256:$sha"
                    },
                    {
                      "name": "insecure.apk",
                      "browser_download_url": "http://example.com/insecure.apk",
                      "size": 1,
                      "digest": "sha256:$sha"
                    },
                    {
                      "name": "missing-digest.apk",
                      "browser_download_url": "https://example.com/no-digest.apk",
                      "size": 2
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val release = repository.latestRelease()

        assertEquals("v0.13.0", release.tagName)
        assertEquals(
            listOf("Gimi-plugin-zhihu-v0.13.0.apk", "missing-digest.apk"),
            release.assets.map { it.name },
        )
        assertEquals(sha, release.assets[0].sha256)
        assertEquals(12345L, release.assets[0].sizeBytes)
        assertNull(release.assets[1].sha256)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun cachesLatestReleaseWithinTtl() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1","assets":[]}"""))

        repository.latestRelease()
        repository.latestRelease()

        assertEquals(1, server.requestCount)
    }

    @Test
    fun rateLimitResponseFails() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0"),
        )

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.latestRelease() }
        }
    }

    @Test
    fun draftReleaseFails() {
        server.enqueue(
            MockResponse().setBody("""{"tag_name":"v1","draft":true,"assets":[]}"""),
        )

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.latestRelease() }
        }
    }

    @Test
    fun nonSuccessfulResponseFails() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.latestRelease() }
        }
    }
}
