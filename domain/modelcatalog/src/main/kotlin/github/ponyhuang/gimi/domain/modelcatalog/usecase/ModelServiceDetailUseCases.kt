package github.ponyhuang.gimi.domain.modelcatalog.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
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

/** 业务空间 ID 允许的字符集：专属域名把它拼进主机名，超出主机名安全字符的 ID 一律拒绝。 */
private val WORKSPACE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

/** 本地格式校验与连接测试分离；网络暂时不可用时仍允许保存合法配置。 */
fun LLMModelSetting.hasValidConnectionConfiguration(): Boolean {
    if (apiKey.isBlank()) return false
    if (apiProtocol !in supportedProtocols) return false
    // 业务空间 ID 会被拼进专属域名的主机名，只放行主机名安全字符。
    workspaceId?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
        if (!WORKSPACE_ID_PATTERN.matches(id)) return false
    }
    val uri = try {
        URI(activeApiBaseUrl.trim())
    } catch (_: java.net.URISyntaxException) {
        return false
    }
    return uri.scheme?.lowercase() in endpointSchemes && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.fragment == null
}

class TestModelServiceConnectionUseCase @Inject constructor(private val remoteGateway: ModelServiceRemoteGateway) {
    suspend operator fun invoke(service: LLMModelSetting): ServiceConnectionResult {
        if (!service.hasValidConnectionConfiguration()) return ServiceConnectionResult.NetworkFailed
        return try {
            remoteGateway.validateConnection(service)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ServiceConnectionResult.NetworkFailed
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
