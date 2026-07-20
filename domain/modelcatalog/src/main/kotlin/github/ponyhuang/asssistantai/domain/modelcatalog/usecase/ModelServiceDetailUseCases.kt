package github.ponyhuang.asssistantai.domain.modelcatalog.usecase

import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelServiceRemoteGateway
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class LoadModelServiceUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    suspend operator fun invoke(serviceId: String): ModelService? {
        repository.awaitReady()
        return repository.currentService(serviceId)
    }
}

class ObserveModelServiceUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    operator fun invoke(serviceId: String): Flow<ModelService?> =
        repository.observeService(serviceId)
}

class ObserveModelServicesUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    operator fun invoke(): Flow<List<ModelService>> = repository.observeServices()
}

class ObserveModelCatalogLoadStateUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    operator fun invoke(): Flow<CatalogLoadState> = repository.observeLoadState()
}

class UpdateModelServiceUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    fun apiKey(serviceId: String, value: String) = repository.updateApiKey(serviceId, value)

    fun enabled(serviceId: String, enabled: Boolean): Boolean =
        repository.updateEnabled(serviceId, enabled)

    fun protocol(serviceId: String, protocol: ApiProtocol) =
        repository.updateApiProtocol(serviceId, protocol)

    fun baseUrl(serviceId: String, value: String) = repository.updateBaseUrl(serviceId, value)

    suspend fun addModel(serviceId: String, model: Model) = repository.addModel(serviceId, model)

    suspend fun removeModel(serviceId: String, groupId: String, modelId: String) =
        repository.removeCatalogModel(serviceId, groupId, modelId)
}

class TestModelServiceConnectionUseCase @Inject constructor(
    private val remoteGateway: ModelServiceRemoteGateway,
) {
    suspend operator fun invoke(service: ModelService): Boolean {
        if (service.apiKey.isBlank()) return false
        return runCatching { remoteGateway.validateConnection(service) }.getOrDefault(false)
    }
}

class RefreshModelCatalogUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
    private val remoteGateway: ModelServiceRemoteGateway,
) {
    suspend operator fun invoke(service: ModelService): Result<Int> = runCatching {
        val models = remoteGateway.fetchModels(service)
        repository.replaceRemoteModels(service.id, models)
        models.size
    }
}
