package github.ponyhuang.asssistantai.data.mcp.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.data.mcp.repository.KeystoreMcpServerStorage
import github.ponyhuang.asssistantai.data.mcp.repository.McpServerStorage
import github.ponyhuang.asssistantai.data.mcp.repository.SecureMcpServerRepository
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class McpModule {
    @Binds
    @Singleton
    abstract fun bindMcpRepository(
        implementation: SecureMcpServerRepository,
    ): McpRepository

    @Binds
    @Singleton
    abstract fun bindMcpServerStorage(
        implementation: KeystoreMcpServerStorage,
    ): McpServerStorage
}
