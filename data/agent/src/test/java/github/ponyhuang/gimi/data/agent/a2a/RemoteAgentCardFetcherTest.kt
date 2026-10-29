package github.ponyhuang.gimi.data.agent.a2a

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 卡片网络边界：真实无响应连接须超时、可取消且不重试、不跨站转发凭据。 */
class RemoteAgentCardFetcherTest {
    @Test
    fun unresponsiveCardTimesOutWithoutRetry() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val fetcher = RemoteAgentCardFetcher(OkHttpClient.Builder()
                .callTimeout(100, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build())

            try {
                withTimeout(2_000) { fetcher.fetch(RemoteAgentConfig(endpointUrl = server.url("/").toString())) }
                fail("Unresponsive card must time out")
            } catch (_: IOException) {
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun cancellationClosesPendingCardRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val job = async(start = CoroutineStart.UNDISPATCHED) {
                RemoteAgentCardFetcher().fetch(RemoteAgentConfig(endpointUrl = server.url("/").toString()))
            }
            checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

            withTimeout(2_000) { job.cancelAndJoin() }

            assertTrue(job.isCancelled)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun redirectsDoNotForwardBearerCredentials() = runBlocking {
        MockWebServer().use { destination ->
            MockWebServer().use { server ->
                destination.start()
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/")))
                server.start()

                try {
                    RemoteAgentCardFetcher().fetch(RemoteAgentConfig(
                        endpointUrl = server.url("/").toString(), bearerToken = "fixture-token",
                    ))
                    fail("Redirect must be rejected")
                } catch (_: IOException) {
                    assertEquals(0, destination.requestCount)
                    assertEquals("Bearer fixture-token", server.takeRequest().getHeader("Authorization"))
                }
            }
        }
    }
}
