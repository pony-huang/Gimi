package github.ponyhuang.gimi.data.a2ui

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.a2ui.A2uiProtocol

@Module
@InstallIn(SingletonComponent::class)
abstract class A2uiProtocolModule {
    @Binds
    abstract fun bindProtocol(implementation: AndroidxA2uiProtocol): A2uiProtocol
}
