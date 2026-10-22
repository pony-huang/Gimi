package github.ponyhuang.gimi.domain.modelcatalog.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class LoadModelServiceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(serviceId: String): LLMModelSetting? {
        repository.awaitReady()
        return repository.currentService(serviceId)
    }
}

class ObserveModelServiceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(serviceId: String): Flow<LLMModelSetting?> = repository.observeService(serviceId)
}

class ObserveModelServicesUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(): Flow<List<LLMModelSetting>> = repository.observeServices()
}

class ObserveModelCatalogLoadStateUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(): Flow<CatalogLoadState> = repository.observeLoadState()
}

class LoadModelCandidatesUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(serviceId: String): List<ModelGroup> = repository.modelCandidates(serviceId)
}

class SaveModelServiceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(service: LLMModelSetting): Boolean =
        service.hasValidConnectionConfiguration() && repository.saveService(service)
}

class SetModelServiceEnabledUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(serviceId: String, enabled: Boolean): Boolean =
        repository.setServiceEnabled(serviceId, enabled)
}

class DeleteModelServiceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(serviceId: String): Boolean = repository.deleteService(serviceId)
}

/** 本地格式校验与连接测试分离；网络暂时不可用时仍允许保存合法配置。 */
fun LLMModelSetting.hasValidConnectionConfiguration(): Boolean {
    if (apiKey.isBlank() || apiProtocol !in supportedProtocols ||
        fileUploadRetentionDays !in setOf(1, 7, 30)) return false
    val uri = try {
        URI(activeApiBaseUrl.trim())
    } catch (_: java.net.URISyntaxException) {
        return false
    }
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.fragment == null
}

class TestModelServiceConnectionUseCase @Inject constructor(private val remoteGateway: ModelServiceRemoteGateway) {
    suspend operator fun invoke(service: LLMModelSetting): Boolean {
        if (!service.hasValidConnectionConfiguration()) return false
        return try {
            remoteGateway.validateConnection(service)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}

/** 只获取候选，不在刷新期间写入仓库。 */
class RefreshModelCatalogUseCase @Inject constructor(private val remoteGateway: ModelServiceRemoteGateway) {
    suspend operator fun invoke(service: LLMModelSetting): Result<List<Model>> = try {
        require(service.hasValidConnectionConfiguration())
        Result.success(remoteGateway.fetchModels(service))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
