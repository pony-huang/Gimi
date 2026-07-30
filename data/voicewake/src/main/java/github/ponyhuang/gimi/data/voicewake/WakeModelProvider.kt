package github.ponyhuang.gimi.data.voicewake

import javax.inject.Inject
import javax.inject.Singleton
import org.vosk.Model

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
