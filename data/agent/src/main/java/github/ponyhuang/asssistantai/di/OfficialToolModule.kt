package github.ponyhuang.asssistantai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.asssistantai.agent.tools.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.anthropic.AnthropicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.glm.GlmWebSearchToolset
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.asssistantai.agent.tools.official.mimo.MimoOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.minimax.MinimaxOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.openai.OpenaiOfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolFunctionCatalog

@Module
@InstallIn(SingletonComponent::class)
abstract class OfficialToolModule {

    @Binds
    abstract fun bindOfficialToolFunctionCatalog(
        catalog: DefaultOfficialToolFunctionCatalog,
    ): OfficialToolFunctionCatalog

    @Binds
    @IntoSet
    abstract fun bindOpenaiToolset(
        toolset: OpenaiOfficialToolset,
    ): OfficialToolset

    @Binds
    @IntoSet
    abstract fun bindAnthropicToolset(
        toolset: AnthropicOfficialToolset,
    ): OfficialToolset

    @Binds
    @IntoSet
    abstract fun bindMiniMaxToolset(
        toolset: MinimaxOfficialToolset,
    ): OfficialToolset

    @Binds
    @IntoSet
    abstract fun bindMimoToolset(
        toolset: MimoOfficialToolset,
    ): OfficialToolset

    @Binds
    @IntoSet
    abstract fun bindKimiFormulaToolset(
        toolset: KimiFormulaToolset,
    ): OfficialToolset

    @Binds
    @IntoSet
    abstract fun bindGlmWebSearchToolset(
        toolset: GlmWebSearchToolset,
    ): OfficialToolset
}
