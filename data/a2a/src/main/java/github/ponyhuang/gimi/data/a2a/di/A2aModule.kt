package github.ponyhuang.gimi.data.a2a.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.a2a.repository.KeystoreRemoteAgentStorage
import github.ponyhuang.gimi.data.a2a.repository.RemoteAgentStorage
import github.ponyhuang.gimi.data.a2a.repository.SecureRemoteAgentRepository
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class A2aModule {
    @Binds
    @Singleton
    abstract fun bindRemoteAgentRepository(
        implementation: SecureRemoteAgentRepository,
    ): RemoteAgentRepository

    @Binds
    @Singleton
    abstract fun bindRemoteAgentStorage(
        implementation: KeystoreRemoteAgentStorage,
    ): RemoteAgentStorage
}
