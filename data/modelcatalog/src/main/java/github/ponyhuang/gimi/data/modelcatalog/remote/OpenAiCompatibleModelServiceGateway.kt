package github.ponyhuang.gimi.data.modelcatalog.remote

import com.google.gson.Gson
import github.ponyhuang.gimi.core.common.coroutine.IoDispatcher
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenAiCompatibleModelServiceGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelServiceRemoteGateway {
    private val gson = Gson()
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
                gson.fromJson(body, OpenAiModelsResponse::class.java)
                    .data
                    .map { remote -> Model(id = remote.id, name = remote.id) }
            }
        }
}

private data class OpenAiModelsResponse(
    val data: List<OpenAiModelEntry> = emptyList(),
)

private data class OpenAiModelEntry(
    val id: String = "",
)
