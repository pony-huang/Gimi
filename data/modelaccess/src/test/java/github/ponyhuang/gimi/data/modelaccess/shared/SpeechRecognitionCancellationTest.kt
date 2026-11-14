package github.ponyhuang.gimi.data.modelaccess.shared

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 必须取消实际的 HTTP Call，防止被打断的录音继续占用网络和服务端识别资源。 */
class SpeechRecognitionCancellationTest {
    @Test
    fun cancellingPendingResponseCancelsHttpCall() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        server.start()
        val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS).build()
        val call = client.newCall(Request.Builder().url(server.url("/speech")).build())
        val request = async(start = CoroutineStart.UNDISPATCHED) { call.awaitSpeechRecognitionBody() }
        try {
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            withTimeout(1_000) { request.cancelAndJoin() }
            assertTrue(call.isCanceled())
        } finally {
            call.cancel()
            request.cancelAndJoin()
            server.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
