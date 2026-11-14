package github.ponyhuang.gimi.data.modelaccess.openaicompatible

import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MIMO_SPEECH_SERVICE_ID
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

/**
 * OpenAI 兼容对话协议语音服务的连通性探测。
 *
 * 用 `GET {baseUrl}/models` 校验端点与密钥：只读目录、不产生计费请求。
 * 2xx 即密钥可用；401/403 归为密钥错误，其余网络/服务端异常统一归为网络失败。
 */
@Singleton
class OpenAiCompatibleConnectionProbe @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : SpeechConnectionProbe {

    // 仅覆盖端点可由用户配置的语音连接；聊天连接（deepseek 等）不在此列，
    // 它们的连接测试由模型目录探测承担。openai 与 qwen 的端点已由厂商自有，
    // 分别由实时握手探测承担连接测试。
    override val serviceIds: Set<String> = setOf(MIMO_SPEECH_SERVICE_ID)

    // 探测是服务级握手（校验密钥可用性），不限定具体能力
    override val capabilities: Set<ModelCapability> = setOf(ModelCapability.TTS)

    override suspend fun probe(connection: ResolvedServiceConnection): ServiceConnectionResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(connection.endpoint.trim().trimEnd('/') + "/models")
                .header("Authorization", "Bearer ${connection.apiKey.trim()}")
                .build()
            try {
                val response = withTimeoutOrNull(PROBE_TIMEOUT_MS.milliseconds) {
                    okHttpClient.newCall(request).execute()
                } ?: return@withContext ServiceConnectionResult.NetworkFailed
                response.use {
                    when {
                        it.isSuccessful -> ServiceConnectionResult.Succeeded
                        it.code == 401 || it.code == 403 -> ServiceConnectionResult.AuthFailed
                        else -> ServiceConnectionResult.NetworkFailed
                    }
                }
            } catch (_: IOException) {
                ServiceConnectionResult.NetworkFailed
            }
        }

    private companion object {
        const val PROBE_TIMEOUT_MS = 15_000L
    }
}
