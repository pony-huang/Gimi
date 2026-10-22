package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.CloudAttachmentReference
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import javax.inject.Inject

/** 从当前仍有权限的连接查找文件；预览读取独立于聊天文件引用能力，不会触发上传。 */
class ResolveCloudFileLocalUseCase @Inject constructor(private val repository: CloudFileRepository) {
    suspend operator fun invoke(services: List<LLMModelSetting>, reference: CloudAttachmentReference): String {
        for (service in services) {
            if (service.apiKey.isBlank()) continue
            if (repository.list(service).none { it.id == reference.fileId && it.scope == reference.scope }) continue
            return repository.resolveLocal(service, reference).localReference
                ?: throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
        }
        throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
    }
}
