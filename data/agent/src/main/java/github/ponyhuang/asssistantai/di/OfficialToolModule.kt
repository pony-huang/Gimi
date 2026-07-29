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
import github.ponyhuang.asssistantai.agent.tools.official.OfficialToolProvider
import github.ponyhuang.asssistantai.agent.tools.official.OpenAiOfficialToolAdapter
import github.ponyhuang.asssistantai.agent.tools.official.WebSearchOfficialToolProvider
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
    abstract fun bindWebSearchProvider(
        provider: WebSearchOfficialToolProvider,
    ): OfficialToolProvider

    @Binds
    @IntoSet
    abstract fun bindOpenaiWebSearchAdapter(
        adapter: OpenAiOfficialToolAdapter,
    ): IOpenAiOfficialToolAdapter

    @Binds
    @IntoSet
    abstract fun bindAnthropicWebSearchAdapter(
        adapter: AnthropicOfficialToolAdapter,
    ): IAnthropicOfficialToolAdapter

}
