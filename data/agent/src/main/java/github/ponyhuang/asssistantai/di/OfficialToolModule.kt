package github.ponyhuang.asssistantai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.asssistantai.agent.tools.official.AnthropicOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.asssistantai.agent.tools.official.IAnthropicOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.IOpenAiOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.OpenAiOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.anthropic.AnthropicOfficialToolset
import github.ponyhuang.asssistantai.agent.tools.official.kimi.KimiFormulaToolset
import github.ponyhuang.asssistantai.agent.tools.official.openai.OpenaiOfficialToolset
import github.ponyhuang.asssistantai.domain.modelcatalog.model.OfficialToolFunctionCatalog

@Module
@InstallIn(SingletonComponent::class)
abstract class OfficialToolModule {

    @Binds
    abstract fun bindOfficialToolFunctionCatalog(
        catalog: DefaultOfficialToolFunctionCatalog,
    ): OfficialToolFunctionCatalog

    // ---- Official toolsets ----
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
    abstract fun bindKimiFormulaToolset(
        toolset: KimiFormulaToolset,
    ): OfficialToolset

    // ---- Protocol adapter sets ----
    @Binds
    @IntoSet
    abstract fun bindOpenaiAdapter(
        adapter: OpenAiOfficialToolAdapter,
    ): IOpenAiOfficialToolAdapter

    @Binds
    @IntoSet
    abstract fun bindAnthropicAdapter(
        adapter: AnthropicOfficialToolAdapter,
    ): IAnthropicOfficialToolAdapter

}
