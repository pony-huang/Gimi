package github.ponyhuang.gimi.data.appupdate.remote

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GitHubReleaseGatewayTest {

    private val server = MockWebServer()
    private lateinit var gateway: GitHubReleaseGateway

    @Before
    fun setUp() {
        server.start()
        gateway = GitHubReleaseGateway(OkHttpClient(), server.url("/releases/latest").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses latest release with digest`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "tag_name": "v0.2.0",
                  "name": "v0.2.0",
                  "body": "bug fixes",
                  "draft": false,
                  "prerelease": false,
                  "published_at": "2026-08-01T00:00:00Z",
                  "assets": [
                    {
                      "name": "Gimi-v0.2.0-arm64-v8a.apk",
                      "browser_download_url": "https://example.com/a.apk",
                      "size": 100,
                      "digest": "sha256:abcd"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val dto = gateway.fetchLatestRelease()
        assertEquals("v0.2.0", dto.tagName)
        assertEquals("sha256:abcd", dto.assets?.first()?.digest)
    }

    @Test
    fun `throws RateLimitException on 403 with exhausted quota`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0"),
        )
        assertThrows(RateLimitException::class.java) {
            gateway.fetchLatestRelease()
        }
    }

    @Test
    fun `throws IOException on server error`() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThrows(IOException::class.java) {
            gateway.fetchLatestRelease()
        }
    }

    @Test
    fun `parses release without digest as null`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "tag_name": "v0.2.0",
                  "draft": false,
                  "prerelease": false,
                  "assets": [
                    {
                      "name": "Gimi-v0.2.0-universal.apk",
                      "browser_download_url": "https://example.com/u.apk",
                      "size": 200
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val dto = gateway.fetchLatestRelease()
        assertNull(dto.assets?.first()?.digest)
    }
}
