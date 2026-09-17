package github.ponyhuang.gimi.data.voicewake

import github.ponyhuang.gimi.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import javax.inject.Inject
import javax.inject.Singleton

/** 将唤醒短语后的纯文本命令提交到共享的当前 Assistant 会话。 */
@Singleton
class VoiceWakeCommandSubmitter @Inject constructor(
    private val coordinator: AssistantSessionCoordinator,
) {
    suspend fun submit(command: String) {
        coordinator.submit(
            text = command,
            source = AssistantInvocationSource.BLUETOOTH_WAKE,
            confirmationHandler = null,
        )
    }
}
