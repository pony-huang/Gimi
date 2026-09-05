package github.ponyhuang.gimi.data.agent.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog

/**
 * 官方工具 DI 装配 — 厂商工具全部收敛进 [github.ponyhuang.gimi.data.agent.tools.official.OfficialToolRegistry]
 * 与 [github.ponyhuang.gimi.data.agent.tools.official.DefaultOfficialToolset](均由构造注入),
 * 无按厂商的工具集绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class OfficialToolModule {

    @Binds
    abstract fun bindOfficialToolFunctionCatalog(
        catalog: DefaultOfficialToolFunctionCatalog,
    ): OfficialToolFunctionCatalog
}
