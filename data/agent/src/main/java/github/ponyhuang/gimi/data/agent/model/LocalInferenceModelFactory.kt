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
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRepository
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** ADK 原生 LiteRT-LM 适配；全应用串行分配引擎，避免多个 GB 级模型同时加载。 */
@Singleton
class LocalInferenceModelFactory @Inject constructor(
    private val repository: LocalModelRepository,
) : LocalModelRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pool = LoadedLocalModelPool { name, config ->
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
        // ADK create() 只创建包装器；必须真正初始化原生引擎后才能发布 Ready。
        try {
            model.engine.initialize()
        } catch (error: Exception) {
            model.close()
            throw error
        } catch (error: LinkageError) {
            model.close()
            throw error
        }
        LocalModelHandle(model, model::close)
    }

    override val state = pool.state

    override suspend fun prepare(modelId: String) {
        repository.awaitReady()
        val config = repository.resolve(modelId)
        if (config == null) pool.unavailable(modelId) else pool.prepare(modelId, config)
    }

    override suspend fun unload() = pool.unload()

    override suspend fun remove(modelId: String) = pool.remove { repository.remove(modelId) }

    override fun release() { scope.launch { pool.unload() } }

    fun create(name: String, config: LocalModelRuntimeConfig): Model = pool.model(name, config)
}
