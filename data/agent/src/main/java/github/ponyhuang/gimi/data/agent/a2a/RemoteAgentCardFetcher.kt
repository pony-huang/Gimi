package github.ponyhuang.gimi.data.agent.a2a

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil
import org.a2aproject.sdk.spec.AgentCard
import org.a2aproject.sdk.spec.util.Utils
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 卡片探测使用可取消、有限时的 HTTP；禁止重试和重定向，避免阻塞聊天或泄露认证头。 */
@Singleton
class RemoteAgentCardFetcher internal constructor(private val client: OkHttpClient) {
    @Inject
    constructor() : this(OkHttpClient.Builder()
        .callTimeout(REMOTE_AGENT_CARD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build())

    /** 取消会关闭实际 HTTP 调用，而非仅取消包围同步阻塞调用的协程。 */
    suspend fun fetch(config: RemoteAgentConfig): AgentCard = suspendCancellableCoroutine { continuation ->
        val cardUrl = Utils.buildCardUrl(Utils.stripWellKnownSuffix(config.endpointUrl), Utils.DEFAULT_AGENT_CARD_PATH)
        val request = Request.Builder().url(cardUrl).header("Accept", "application/json").apply {
            if (config.bearerToken.isNotBlank()) header("Authorization", "Bearer ${config.bearerToken}")
        }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val card = response.use {
                        if (!it.isSuccessful) throw IOException("Failed to fetch agent card: HTTP ${it.code}")
                        // 沿用 SDK 的响应大小上限，防止异常服务返回无限大的卡片。
                        val source = it.body.source()
                        source.request(MAX_CARD_BYTES + 1L)
                        if (source.buffer.size > MAX_CARD_BYTES) throw IOException("Agent card exceeds size limit")
                        val bytes = source.buffer.readByteArray()
                        JsonUtil.fromJson(bytes.toString(Charsets.UTF_8), AgentCard::class.java)
                            ?: throw IOException("Empty agent card response")
                    }
                    continuation.resume(card)
                } catch (failure: Exception) {
                    continuation.resumeWithException(failure)
                }
            }
        })
    }

    private companion object {
        const val MAX_CARD_BYTES = 10 * 1024 * 1024
    }
}
