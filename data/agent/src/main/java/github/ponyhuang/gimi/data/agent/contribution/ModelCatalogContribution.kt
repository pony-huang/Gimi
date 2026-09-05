package github.ponyhuang.gimi.data.agent.contribution

import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型目录贡献方：不贡献工具，只把模型服务配置的 revision 纳入 Agent 缓存键，
 * 使模型服务增删 / 启停 / 编辑后已缓存的 Agent 运行时失效重建。模型实例本身的
 * 解析与构建由 [github.ponyhuang.gimi.data.agent.AgentFactory] 委托
 * [github.ponyhuang.gimi.data.agent.AgentLLMModelFactory] 完成。
 */
@Singleton
class ModelCatalogContribution @Inject constructor(
    private val modelServices: AgentModelConfigurationSource,
) : AgentContribution {

    override val id: String = ID

    override fun revision(): Any = modelServices.configurationRevision.value

    private companion object {
        const val ID: String = "model"
    }
}
