package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.core.storage.WorkspaceStorageIds
import java.io.File
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileChatSupport
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CloudFileModule {
    @Provides
    fun localSource(implementation: AndroidCloudFileLocalSource): github.ponyhuang.gimi.domain.conversation.cloud.CloudFileLocalSource = implementation

    @Provides
    @Singleton
    fun repository(storage: EncryptedCloudFileStorage, gateway: ProviderCloudFileGateway, registry: StorageRegistry): CloudFileRepository =
        DefaultCloudFileRepository(storage, gateway,
            File(registry.resolve(WorkspaceStorageIds.WORKSPACE, create = true), "cloud-files"))

    @Provides
    @Singleton
    fun chatSupport(): CloudFileChatSupport = OpenAiCompatibleCloudFileChatSupport()
}
