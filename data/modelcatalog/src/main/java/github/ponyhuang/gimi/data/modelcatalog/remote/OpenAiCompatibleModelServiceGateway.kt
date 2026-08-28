package github.ponyhuang.gimi.data.modelcatalog.remote

import github.ponyhuang.gimi.core.common.coroutine.IoDispatcher
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenAiCompatibleModelServiceGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelServiceRemoteGateway {
    // 服务端可能返回未声明的额外字段，Gson 静默忽略、kotlinx 默认抛异常，这里显式忽略。
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validateConnection(service: LLMModelSetting): Boolean =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url("${service.openAiCompatibleBaseUrl}/models")
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${service.apiKey.trim()}")
                .build()

            okHttpClient.newCall(request).execute().use { response -> response.code == 200 }
        }

    override suspend fun fetchModels(service: LLMModelSetting): List<Model> =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url("${service.openAiCompatibleBaseUrl}/models")
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${service.apiKey.trim()}")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Model list request failed with HTTP ${response.code}"
                }
                val body = checkNotNull(response.body).string()
                json.decodeFromString<OpenAiModelsResponse>(body)
                    .data
                    .map { remote -> Model(id = remote.id, name = remote.id) }
            }
        }
}

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModelEntry> = emptyList(),
)

@Serializable
private data class OpenAiModelEntry(
    val id: String = "",
)
