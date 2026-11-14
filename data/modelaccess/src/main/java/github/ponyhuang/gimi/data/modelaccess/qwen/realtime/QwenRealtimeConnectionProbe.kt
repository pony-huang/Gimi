package github.ponyhuang.gimi.data.modelaccess.qwen.realtime

import github.ponyhuang.gimi.core.network.VoiceWsKernel
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import github.ponyhuang.gimi.domain.voice.repository.VoiceHandshakeException
import github.ponyhuang.gimi.domain.voice.repository.voiceHandshakeFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.milliseconds

/** 千问语音服务标识；实时与级联模型共处该服务，与模型目录中的服务 ID 一致。 */
internal const val QWEN_AUDIO_SERVICE_ID = "qwen"

/**
 * 千问语音服务（实时握手）的连通性探测。
 *
 * 只做一次 WebSocket 握手，不下发 `session.update`：百炼在握手阶段即校验密钥
 * （无效 key 直接 401，连接不升级），握手成功即证明密钥可用，也不会创建会话
 * 产生计费。模型以查询参数携带，探测使用 3.1 预设模型——握手校验的是密钥而非
 * 模型可用性。级联识别/合成与实时会话共用同一密钥与业务空间，实时端点探测通过
 * 即整服务可用；端点由厂商自有，业务空间 ID 非空时改写专属域名并携带请求头。
 */
@Singleton
class QwenRealtimeConnectionProbe @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechConnectionProbe {

    override val serviceIds: Set<String> = setOf(QWEN_AUDIO_SERVICE_ID)

    // 探测是服务级握手（校验密钥可用性），不限定具体能力
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    override val endpointStrategy: EndpointStrategy = EndpointStrategy.VendorOwned

    override suspend fun probe(connection: ResolvedServiceConnection): ServiceConnectionResult =
        withContext(Dispatchers.IO) {
            val session = VoiceWsKernel(
                client = okHttpClient,
                request = QwenRealtimeProtocol.connectRequest(
                    baseUrl = SpeechEndpoints.qwenRealtimeUrl(connection.workspaceId),
                    model = PROBE_MODEL,
                    apiKey = connection.apiKey.trim(),
                    workspaceId = connection.workspaceId,
                ),
                handshakeFailure = ::voiceHandshakeFailure,
            )
            try {
                val frame = withTimeoutOrNull(PROBE_TIMEOUT_MS.milliseconds) { session.frames().receive() }
                    ?: return@withContext ServiceConnectionResult.NetworkFailed
                when (frame) {
                    is VoiceWsKernel.WsFrame.Opened -> ServiceConnectionResult.Succeeded
                    is VoiceWsKernel.WsFrame.Failure -> frame.error.toConnectionResult()
                    else -> ServiceConnectionResult.NetworkFailed
                }
            } finally {
                session.close()
            }
        }

    private fun Throwable.toConnectionResult(): ServiceConnectionResult {
        val handshake = this as? VoiceHandshakeException ?: return ServiceConnectionResult.NetworkFailed
        return when (handshake.statusCode) {
            401, 403 -> ServiceConnectionResult.AuthFailed
            else -> ServiceConnectionResult.NetworkFailed
        }
    }

    private companion object {
        const val PROBE_MODEL = "qwen-audio-3.1-realtime-plus"
        const val PROBE_TIMEOUT_MS = 15_000L
    }
}
