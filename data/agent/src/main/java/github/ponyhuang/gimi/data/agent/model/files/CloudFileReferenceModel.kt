package github.ponyhuang.gimi.data.agent.model.files

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileAttachmentCodec
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileChatSupport
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFilePreparation
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileRepository
import github.ponyhuang.gimi.domain.conversation.cloud.PrepareCloudFileUseCase
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

/** 每次请求（包含工具续轮和历史重放）重新验证当前连接、能力、总量及有效期。 */
internal class CloudFileReferenceModel(
    private val config: ModelConfig,
    private val services: AgentModelConfigurationSource,
    private val repository: CloudFileRepository,
    private val support: CloudFileChatSupport,
    private val delegate: Model,
    private val fileDelegate: Model,
) : Model {
    override val name = delegate.name

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val cloudParts = request.contents.flatMap { content -> content.parts.mapNotNull { part ->
            val uri = part.fileData?.fileUri ?: return@mapNotNull null
            if (!CloudFileAttachmentCodec.isCloudReference(uri)) return@mapNotNull null
            if (content.role != Role.USER || part.thought == true) fail(CloudFileFailure.InvalidResponse)
            uri to try {
                CloudFileAttachmentCodec.decode(uri, requireNotNull(part.fileData?.mimeType), part.fileData?.displayName.orEmpty())
            } catch (_: IllegalArgumentException) { fail(CloudFileFailure.InvalidResponse) }
        } }
        if (cloudParts.isEmpty()) {
            emitAll(delegate.generateContent(request, stream))
            return@flow
        }
        val service = services.currentServices().firstOrNull { it.id == config.serviceId }
            ?: fail(CloudFileFailure.InvalidConfiguration)
        if (!service.isEnabled || !service.isFileUploadEnabled || service.apiKey.trim() != config.apiKey.trim() ||
            service.apiProtocol != config.baseType || service.activeApiBaseUrl.trim().trimEnd('/') != config.fullBaseUrl.trim().trimEnd('/')) {
            fail(CloudFileFailure.InvalidConfiguration)
        }
        val records = repository.list(service)
        var total = 0L
        cloudParts.forEach { (_, attachment) ->
            val reference = requireNotNull(attachment.cloudReference)
            val record = records.firstOrNull { it.id == reference.fileId && it.scope == reference.scope }
                ?: fail(CloudFileFailure.InvalidConfiguration)
            if (record.mimeType != attachment.mimeType || record.sizeBytes != attachment.sizeBytes) fail(CloudFileFailure.InvalidResponse)
            if (!support.supports(service, config.modelId, record.mimeType)) fail(CloudFileFailure.UnverifiedChatReference)
            if (record.sizeBytes <= 0 || record.sizeBytes > github.ponyhuang.gimi.domain.conversation.cloud.CloudFileRequestLimits.MAX_BYTES - total) fail(CloudFileFailure.FileTooLarge)
            total += record.sizeBytes
        }
        val prepare = PrepareCloudFileUseCase(repository, support)
        val resolved = mutableMapOf<String, String>()
        var earliestExpiry = Long.MAX_VALUE
        cloudParts.forEach { (uri, attachment) ->
            val reference = requireNotNull(attachment.cloudReference)
            var ready: github.ponyhuang.gimi.domain.conversation.cloud.CloudFile? = null
            prepare.existing(service, config.modelId, reference.fileId, reference.scope).collect { result ->
                when (result) {
                    is CloudFilePreparation.Ready -> ready = result.file
                    is CloudFilePreparation.Failed -> fail(result.reason)
                    CloudFilePreparation.Uploading -> Unit
                }
            }
            val file = ready ?: fail(CloudFileFailure.InvalidResponse)
            if (file.expiresAtMillis <= System.currentTimeMillis()) fail(CloudFileFailure.LocalFileUnavailable)
            earliestExpiry = minOf(earliestExpiry, file.expiresAtMillis)
            resolved[uri] = CloudFileAttachmentCodec.encode(attachment.copy(
                cloudReference = github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference(file.id, file.scope),
                payloadReference = file.localReference,
            ))
        }
        // 只替换本次请求副本；持久化历史保留可再次解析的应用引用，绝不写入 API 密钥。
        val prepared = request.copy(contents = request.contents.map { content -> content.copy(parts = content.parts.map { part ->
            val file = part.fileData
            val uri = file?.fileUri
            if (uri != null && uri in resolved) part.copy(fileData = file.copy(fileUri = resolved.getValue(uri))) else part
        }) })
        if (services.currentServices().firstOrNull { it.id == service.id } != service) fail(CloudFileFailure.InvalidConfiguration)
        if (earliestExpiry <= System.currentTimeMillis()) fail(CloudFileFailure.LocalFileUnavailable)
        emitAll(fileDelegate.generateContent(prepared, stream))
    }

    private fun fail(reason: CloudFileFailure): Nothing = throw CloudFileException(reason)
}
