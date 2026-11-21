package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.Message
import javax.inject.Inject

/**
 * 判定会话是否因包含厂商 file 引用附件而锁定到某厂商。
 *
 * 锁定依据完全从消息推导（附件携带 fileId 即视为"用过上传"），不存额外状态标记：
 * 同厂商内换模型允许（file_id 账号级共享），跨厂商切换入口由 UI 禁用并提示。
 * 开关的准入语义只管新附件，已锁会话不受后续开关状态影响。
 *
 * @return 锁定到的厂商模板 ID（如 `deepseek`）；会话无 file 引用附件时为 null。
 */
class DetectConversationVendorLockUseCase @Inject constructor() {
    operator fun invoke(messages: List<Message>): String? =
        messages.asSequence()
            .flatMap { it.fileAttachments.asSequence() }
            .firstOrNull { it.fileId != null && it.fileVendor != null }
            ?.fileVendor
}
