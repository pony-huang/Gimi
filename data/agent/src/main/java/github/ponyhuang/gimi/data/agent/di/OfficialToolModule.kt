package github.ponyhuang.gimi.data.agent.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.gimi.data.agent.tools.official.OfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.anthropic.AnthropicOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.gemini.GeminiOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.glm.GlmWebSearchToolset
import github.ponyhuang.gimi.data.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.gimi.data.agent.tools.official.mimo.MimoOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.minimax.MinimaxOfficialToolset
import github.ponyhuang.gimi.data.agent.tools.official.openai.OpenaiOfficialToolset
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog

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

    @Binds
    @IntoSet
    abstract fun bindGeminiToolset(
        toolset: GeminiOfficialToolset,
    ): OfficialToolset
}
