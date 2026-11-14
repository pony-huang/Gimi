package github.ponyhuang.gimi.domain.modelcatalog.usecase

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.model.TemplateConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class LoadModelInstanceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(instanceId: String): ModelServiceInstance? {
        repository.awaitReady()
        return repository.currentInstance(instanceId)
    }
}

class ObserveModelInstanceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(instanceId: String): Flow<ModelServiceInstance?> =
        repository.observeInstance(instanceId)
}

class ObserveModelInstancesUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(): Flow<List<ModelServiceInstance>> = repository.observeInstances()
}

class ObserveModelTemplatesUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(): Flow<List<ModelServiceTemplate>> = repository.observeTemplates()
}

class ObserveModelCatalogLoadStateUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    operator fun invoke(): Flow<CatalogLoadState> = repository.observeLoadState()
}

/** 连接候选目录：模板内置目录与该实例已选模型合并，供模型管理挑选。 */
class LoadModelCandidatesUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(instanceId: String, connectionId: String): List<Model> =
        repository.modelCandidates(instanceId, connectionId)
}

/** 保存新实例；仓库分配稳定 ID 并整体落库，失败返回 null。 */
class SaveNewModelInstanceUseCase @Inject constructor(
    private val repository: ModelCatalogRepository,
) {
    suspend operator fun invoke(draft: ModelServiceDraft, template: ModelServiceTemplate?): ModelServiceInstance? =
        draft.takeIf { it.hasValidConfiguration(template) }?.let { repository.saveNewInstance(it) }
}

/** 保存既有实例；一次提交连接、凭据、模型与工具偏好，失败保持原状态。 */
class SaveModelInstanceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(draft: ModelServiceDraft, template: ModelServiceTemplate?): Boolean =
        draft.hasValidConfiguration(template) && repository.saveInstance(draft)
}

/** 独立启停；停用保留配置与默认引用。 */
class SetModelInstanceEnabledUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(instanceId: String, enabled: Boolean): Boolean =
        repository.setInstanceEnabled(instanceId, enabled)
}

/** 删除实例；同一提交内清理受影响默认分配与绑定音色。 */
class DeleteModelInstanceUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(instanceId: String): Boolean = repository.deleteInstance(instanceId)
}

/** 删除影响查询；确认对话框据此展示受影响默认用途。 */
class DeletionImpactUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(instanceId: String): DeletionImpact = repository.deletionImpact(instanceId)
}

/**
 * 可选连接测试；输入是草稿构建的连接视图，测试结果不影响保存资格。
 * 本地格式校验失败按网络失败报告，避免把配置错误当作服务端异常。
 */
class TestModelServiceConnectionUseCase @Inject constructor(private val remoteGateway: ModelServiceRemoteGateway) {
    suspend operator fun invoke(connection: ResolvedServiceConnection): ServiceConnectionResult {
        if (!connection.hasValidConnectionConfiguration()) return ServiceConnectionResult.NetworkFailed
        return try {
            remoteGateway.validateConnection(connection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ServiceConnectionResult.NetworkFailed
        }
    }
}

/** 只获取候选，不在刷新期间写入仓库；输入是草稿构建的连接视图。 */
class RefreshModelCatalogUseCase @Inject constructor(private val remoteGateway: ModelServiceRemoteGateway) {
    suspend operator fun invoke(connection: ResolvedServiceConnection): Result<List<Model>> = try {
        require(connection.hasValidConnectionConfiguration())
        Result.success(remoteGateway.fetchModels(connection))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

/**
 * 从模板或自定义协议构建新实例草稿。
 *
 * 模板连接全部铺开（用户在服务详情里增删模型），自定义服务只建一条对话连接；
 * 连接 ID 由本用例生成并在保存后保持稳定。
 */
class CreateModelServiceDraftUseCase @Inject constructor(private val repository: ModelCatalogRepository) {
    suspend operator fun invoke(
        templateId: String?,
        name: String,
        protocol: ModelServiceDraftProtocol = ModelServiceDraftProtocol.OpenaiChatcompletions,
    ): ModelServiceDraft {
        val template = templateId?.let { repository.template(it) }
        return if (template != null) {
            ModelServiceDraft(
                instanceId = null,
                templateId = template.id,
                name = name,
                isOfficialToolsEnabled = true,
                sharedApiKey = "",
                connections = template.connections.map { it.toConnection() },
            )
        } else {
            ModelServiceDraft(
                instanceId = null,
                templateId = null,
                name = name,
                isOfficialToolsEnabled = false,
                sharedApiKey = "",
                connections = listOf(
                    ServiceConnection(
                        id = newConnectionId(ServiceCapability.Conversation),
                        capability = ServiceCapability.Conversation,
                        protocol = protocol.toApiProtocol(),
                        endpoint = "",
                        credential = CredentialSource.Shared,
                    ),
                ),
            )
        }
    }
}

/** 自定义服务创建时可选择的接入协议。 */
enum class ModelServiceDraftProtocol { OpenaiChatcompletions, Anthropic }

/** 自定义服务协议选择到 API 协议的映射。 */
fun ModelServiceDraftProtocol.toApiProtocol() = when (this) {
    ModelServiceDraftProtocol.OpenaiChatcompletions -> ApiProtocol.OpenaiChatcompletions
    ModelServiceDraftProtocol.Anthropic -> ApiProtocol.Anthropic
}

/** 模板连接派生用户连接：协议与端点取模板首选项，凭据默认共享，目录留给模型管理。 */
fun TemplateConnection.toConnection(): ServiceConnection {
    val protocol = protocols.first()
    return ServiceConnection(
        id = newConnectionId(capability),
        capability = capability,
        protocol = protocol,
        endpoint = defaultEndpoint(protocol),
        credential = CredentialSource.Shared,
    )
}

/** 生成一条连接稳定 ID；同一实例内类别足以区分，保存后不再变化。 */
fun newConnectionId(capability: ServiceCapability): String =
    "conn-${capability.name.lowercase()}-${newConnectionIdToken()}"

private fun newConnectionIdToken(): String = UUID.randomUUID().toString().substringBefore('-')

/**
 * 草稿保存前的本地结构校验。
 *
 * 规则：至少一条连接已选模型；已选模型的连接必须结构完整（协议、端点、凭据）；
 * 业务空间 ID 仅在模板支持时按主机名安全字符校验。未选模型的能力连接不校验、
 * 不阻碍保存。连接测试是可选操作，不参与本校验。
 */
fun ModelServiceDraft.hasValidConfiguration(template: ModelServiceTemplate?): Boolean {
    val selected = connections.filter { it.models.isNotEmpty() }
    if (selected.isEmpty()) return false
    if (selected.any { it.credential == CredentialSource.Shared && sharedApiKey.isBlank() }) return false
    return selected.all { connection ->
        val templateConnection = template?.connection(connection.capability)
        if (templateConnection?.supportsWorkspace == true && !workspaceIdIsValid(connection.workspaceId)) {
            return false
        }
        connection.hasValidConnectionConfiguration()
    }
}

/** 单条连接的结构校验：端点合法、独立凭据非空；共享凭据由 [hasValidConfiguration] 整体判断。 */
fun ServiceConnection.hasValidConnectionConfiguration(): Boolean {
    if (!endpoint.isWellFormedUrl()) return false
    return when (val source = credential) {
        CredentialSource.Shared -> true
        is CredentialSource.Independent -> source.apiKey.isNotBlank()
    }
}

/** 连接视图的端点与凭据校验；连接测试入口复用，语义与草稿校验一致。 */
fun ResolvedServiceConnection.hasValidConnectionConfiguration(): Boolean =
    apiKey.isNotBlank() && endpoint.isWellFormedUrl()

/** 业务空间 ID 允许的字符集：专属域名把它拼进主机名，超出主机名安全字符的 ID 一律拒绝。 */
private val WORKSPACE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

/** 业务空间 ID 校验；仅支持业务空间的连接（百炼）消费该字段。 */
fun workspaceIdIsValid(workspaceId: String?): Boolean {
    val id = workspaceId?.trim().orEmpty()
    if (id.isEmpty()) return true
    return WORKSPACE_ID_PATTERN.matches(id)
}

private fun String.isWellFormedUrl(): Boolean {
    val uri = try {
        URI(trim())
    } catch (_: java.net.URISyntaxException) {
        return false
    }
    return uri.scheme?.lowercase() in setOf("http", "https", "wss") && !uri.host.isNullOrBlank() &&
        uri.userInfo == null && uri.fragment == null
}

/** 供 UI 判断端点是否允许编辑。 */
fun ModelServiceTemplate.endpointPolicy(capability: ServiceCapability): EndpointPolicy? =
    connection(capability)?.endpointPolicy

/** 删除影响是否包含某角色。 */
fun DeletionImpact.affects(role: DefaultModelRole): Boolean = role in roles
