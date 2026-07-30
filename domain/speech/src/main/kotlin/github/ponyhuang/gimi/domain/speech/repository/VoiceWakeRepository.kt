package github.ponyhuang.gimi.domain.speech.repository

import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import kotlinx.coroutines.flow.StateFlow

interface VoiceWakeRepository {
    val state: StateFlow<VoiceWakeState>

    /** 设置激活模型语言的唤醒词。 */
    fun setKeyword(keyword: String): Result<Unit>

    /** 切换激活的唤醒模型。 */
    fun selectModel(modelId: String)

    /** 安装（内置解包或下载）指定模型。 */
    fun installModel(modelId: String)

    /** 取消正在进行的模型安装/下载。 */
    fun cancelInstall(modelId: String)

    fun start()

    fun stop()
}
