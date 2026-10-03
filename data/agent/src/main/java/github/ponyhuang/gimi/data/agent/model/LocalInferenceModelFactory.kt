package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.models.Model
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelBackend
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelRuntimeConfig
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/** ADK 原生 LiteRT-LM 适配；全应用串行分配引擎，避免多个 GB 级模型同时加载。 */
@Singleton
class LocalInferenceModelFactory @Inject constructor() {
    private val inferenceMutex = Mutex()

    fun create(name: String, config: LocalModelRuntimeConfig): Model = RequestScopedLocalModel(name, inferenceMutex) {
        check(File(config.modelPath).isFile) { "Local model file is unavailable" }
        // 编译权重缓存也可能很大，由模型文件的拥有方在移除版本时一并清理。
        val cache = File(config.modelPath + ".cache").apply { mkdirs() }
        val model = LiteRtLmModel.create(
            EngineConfig(
                modelPath = config.modelPath,
                backend = when (config.backend) {
                    LocalModelBackend.CPU -> Backend.CPU()
                    LocalModelBackend.GPU -> Backend.GPU()
                },
                cacheDir = cache.absolutePath,
                // 现有系统提示和工具声明已超过 SDK 默认 4096 token，保留对话生成空间。
                maxNumTokens = 8192,
            ),
            name = name,
        )
        LocalModelHandle(model, model::close)
    }
}
