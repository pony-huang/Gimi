package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** 在产生远端上传副作用之前检查当前模型引用能力；不切换协议、不回退内联。 */
class PrepareCloudFileUseCase @Inject constructor(
    private val repository: CloudFileRepository,
    private val chatSupport: CloudFileChatSupport,
) {
    operator fun invoke(
        service: LLMModelSetting,
        modelId: String,
        attachment: DraftAttachment,
    ): Flow<CloudFilePreparation> = flow {
        when {
            !service.isFileUploadEnabled -> emit(CloudFilePreparation.Failed(CloudFileFailure.Disabled))
            !chatSupport.supports(service, modelId, attachment.mimeType) ->
                emit(CloudFilePreparation.Failed(CloudFileFailure.UnverifiedChatReference))
            else -> emitAll(repository.prepare(service, attachment))
        }
    }

    /** 云文件也在当前连接下重新校验，不信任其它会话传来的文件 ID 或 MIME 类型。 */
    fun existing(service: LLMModelSetting, modelId: String, fileId: String, expectedScope: String? = null): Flow<CloudFilePreparation> = flow {
        if (!service.isFileUploadEnabled) {
            emit(CloudFilePreparation.Failed(CloudFileFailure.Disabled))
            return@flow
        }
        val record = try {
            repository.list(service).firstOrNull { it.id == fileId }
        } catch (failure: CloudFileException) {
            emit(CloudFilePreparation.Failed(failure.reason))
            return@flow
        }
        when {
            record != null && expectedScope != null && record.scope != expectedScope ->
                emit(CloudFilePreparation.Failed(CloudFileFailure.InvalidConfiguration))
            record == null -> emit(CloudFilePreparation.Failed(CloudFileFailure.LocalFileUnavailable))
            !chatSupport.supports(service, modelId, record.mimeType) ->
                emit(CloudFilePreparation.Failed(CloudFileFailure.UnverifiedChatReference))
            else -> emitAll(repository.prepareExisting(service, fileId))
        }
    }
}
