package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.models.Model
import github.ponyhuang.gimi.data.modelaccess.DefaultAgentLLMModelFactory
import github.ponyhuang.gimi.domain.modelaccess.ModelConfig
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 编排层的模型运行时门面。
 *
 * 模型选择走 [github.ponyhuang.gimi.domain.modelaccess.AgentLlmModelSource] 契约；
 * 协议装配（[DefaultAgentLLMModelFactory.createModel]，返回 ADK [Model]）的 ADK 类型
 * 边界无法经 domain 契约表达（domain 禁止依赖 ADK），收敛为本类对适配层的唯一直连，
 * 其余编排代码不再 import 任何厂商适配类。
 */
@Singleton
class AgentModelRuntime @Inject constructor(
    private val models: DefaultAgentLLMModelFactory,
) {
    fun selectModelConfig(explicitSelection: ModelSelection?): ModelConfig = models.selectModelConfig(explicitSelection)

    fun selectFastModelConfig(): ModelConfig? = models.selectFastModelConfig()

    fun forRecommendationJson(config: ModelConfig): ModelConfig = models.forRecommendationJson(config)

    fun createModel(cfg: ModelConfig): Model = models.createModel(cfg)
}
