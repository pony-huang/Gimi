package github.ponyhuang.gimi.data.modelcatalog.remote

import github.ponyhuang.gimi.core.common.coroutine.IoDispatcher
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OpenAiCompatibleModelServiceGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelServiceRemoteGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validateConnection(service: LLMModelSetting): Boolean = withContext(ioDispatcher) {
        execute(modelsRequest(service)).use { it.code == 200 }
    }

    override suspend fun fetchModels(service: LLMModelSetting): List<Model> = withContext(ioDispatcher) {
        execute(modelsRequest(service)).use { response ->
            check(response.isSuccessful) { "Model list request failed with HTTP ${response.code}" }
            json.decodeFromString<OpenAiModelsResponse>(response.body.string()).data
                .filter { it.id.isNotBlank() }.distinctBy { it.id }.map { Model(it.id, it.id) }
        }
    }

    private fun modelsRequest(service: LLMModelSetting): Request {
        val builder = Request.Builder().get().header("Accept", "application/json")
        if (service.apiProtocol == ApiProtocol.Anthropic) {
            val base = service.activeApiBaseUrl.trim().trimEnd('/')
            builder.url(if (base.endsWith("/v1")) "$base/models" else "$base/v1/models")
                .header("x-api-key", service.apiKey.trim()).header("anthropic-version", "2023-06-01")
        } else {
            // Gemini 当前仅通过兼容端点提供目录检测，聊天仍使用原生实现。
            builder.url("${service.openAiCompatibleBaseUrl}/models")
                .header("Authorization", "Bearer ${service.apiKey.trim()}")
        }
        return builder.build()
    }

    /** 编辑连接或退出时取消底层请求；取消发生在回调交付之后时也关闭响应体。 */
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = okHttpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, unconsumed, _ -> unconsumed.close() }
            }
        })
    }
}

/** OpenAI 兼容和 Anthropic 模型列表共有的 data 数组。 */
@Serializable
private data class OpenAiModelsResponse(val data: List<OpenAiModelEntry> = emptyList())

/** 远端模型的精确请求 ID；其余元数据由本地目录补充。 */
@Serializable
private data class OpenAiModelEntry(val id: String = "")
