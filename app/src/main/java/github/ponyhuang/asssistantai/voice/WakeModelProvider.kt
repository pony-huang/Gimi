package github.ponyhuang.asssistantai.voice

import javax.inject.Inject
import javax.inject.Singleton
import org.vosk.Model

/**
 * 缓存已加载的 Vosk 唤醒模型。模型加载涉及约 40MB native 内存，
 * 每次语音指令后重载会造成秒级监听空窗，因此按路径缓存、跨指令复用。
 */
@Singleton
class WakeModelProvider @Inject constructor() {
    private var cachedPath: String? = null
    private var cachedModel: Model? = null

    @Synchronized
    fun acquire(modelPath: String): Model {
        val existing = cachedModel
        if (existing != null && cachedPath == modelPath) return existing
        existing?.close()
        val model = Model(modelPath)
        cachedPath = modelPath
        cachedModel = model
        return model
    }

    @Synchronized
    fun release() {
        cachedModel?.close()
        cachedModel = null
        cachedPath = null
    }
}
