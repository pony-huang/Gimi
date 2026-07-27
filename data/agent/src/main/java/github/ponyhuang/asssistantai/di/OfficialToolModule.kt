package github.ponyhuang.asssistantai.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.asssistantai.agent.tools.official.AnthropicOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.KimiFormulaOfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.MimoWebSearchToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.MiniMaxWebSearchToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.OpenAiOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.WebSearchOfficialToolProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class OfficialToolModule {

    @Binds
    @IntoSet
    abstract fun bindWebSearchProvider(
        provider: WebSearchOfficialToolProvider,
    ): OfficialToolProvider

    @Binds
    @IntoSet
    abstract fun bindKimiFormulaProvider(
        provider: KimiFormulaOfficialToolProvider,
    ): OfficialToolProvider

    @Binds
    @IntoSet
    abstract fun bindMimoWebSearchAdapter(
        adapter: MimoWebSearchToolAdapter,
    ): OpenAiOfficialToolAdapter

    @Binds
    @IntoSet
    abstract fun bindMiniMaxWebSearchAdapter(
        adapter: MiniMaxWebSearchToolAdapter,
    ): AnthropicOfficialToolAdapter
}
