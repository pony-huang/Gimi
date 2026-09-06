package github.ponyhuang.gimi.data.agent.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.contribution.LocalToolContribution
import github.ponyhuang.gimi.data.agent.contribution.McpToolContribution
import github.ponyhuang.gimi.data.agent.contribution.BaseToolContribution
import github.ponyhuang.gimi.data.agent.contribution.ModelCatalogContribution
import github.ponyhuang.gimi.data.agent.contribution.OfficialToolContribution
import github.ponyhuang.gimi.data.agent.contribution.PluginToolContribution
import github.ponyhuang.gimi.data.agent.contribution.SkillToolContribution

/**
 * Agent 能力贡献方多绑定注册。
 *
 * 新增一个配置源（工具集、revision 来源或工具目录）时，实现 [AgentContribution]
 * 后在此追加一行 `@Binds @IntoSet` 即完成接入；[AgentFactory]、Agent 运行时缓存键
 * 与推荐旁路聚合随之自动生效。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentContributionModule {

    @Binds
    @IntoSet
    abstract fun bindLocalToolContribution(implementation: LocalToolContribution): AgentContribution

    @Binds
    @IntoSet
    abstract fun bindMcpToolContribution(implementation: McpToolContribution): AgentContribution

    @Binds
    @IntoSet
    abstract fun bindOfficialToolContribution(implementation: OfficialToolContribution): AgentContribution

    @Binds
    @IntoSet
    abstract fun bindSkillToolContribution(implementation: SkillToolContribution): AgentContribution

    @Binds
    @IntoSet
    abstract fun bindPluginToolContribution(implementation: PluginToolContribution): AgentContribution

    @Binds
    @IntoSet
    abstract fun bindMemoryToolContribution(implementation: BaseToolContribution): AgentContribution

    @Binds
    @IntoSet
    abstract fun bindModelCatalogContribution(implementation: ModelCatalogContribution): AgentContribution
}
