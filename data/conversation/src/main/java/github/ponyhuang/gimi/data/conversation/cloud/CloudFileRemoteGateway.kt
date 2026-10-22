package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFile
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileException
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import java.io.File

/** 仅表示服务接受了上传，不表示当前模型或聊天协议支持引用。时间单位为毫秒。 */
internal data class UploadedCloudFile(
    val id: String,
    val createdAtMillis: Long,
    val remoteExpiresAtMillis: Long?,
)

internal interface CloudFileRemoteGateway {
    /** 下载原始内容至仓库提供的临时文件；不支持下载的厂商不得猜测端点。 */
    suspend fun download(service: LLMModelSetting, file: CloudFile, target: File) {
        throw CloudFileException(CloudFileFailure.LocalFileUnavailable)
    }
    suspend fun upload(service: LLMModelSetting, attachment: DraftAttachment, source: File): UploadedCloudFile
}
