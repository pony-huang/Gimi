package github.ponyhuang.asssistantai.data.modelcatalog.remote

import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.asssistantai.core.common.coroutine.IoDispatcher
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelServiceRemoteGateway
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OpenAiCompatibleModelServiceGateway @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelServiceRemoteGateway {
    override suspend fun validateConnection(service: ModelService): Boolean =
        withContext(ioDispatcher) {
            val request = Request.Builder()
                .url("${service.openAiCompatibleBaseUrl}/models")
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer ${service.apiKey.trim()}")
                .build()

            okHttpClient.newCall(request).execute().use { response -> response.code == 200 }
        }

    override suspend fun fetchModels(service: ModelService): List<Model> =
        withContext(ioDispatcher) {
            val client = OpenAIOkHttpClient.builder()
                .baseUrl(service.openAiCompatibleBaseUrl)
                .apiKey(service.apiKey)
                .build()

            client.models().list().data().map { remote ->
                Model(id = remote.id(), name = remote.id())
            }
        }
}
