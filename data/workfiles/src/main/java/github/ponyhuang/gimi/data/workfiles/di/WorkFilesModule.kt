package github.ponyhuang.gimi.data.workfiles.di

import dagger.Binds
import dagger.Provides
import dagger.multibindings.IntoSet
import androidx.datastore.core.DataStoreFactory
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.workfiles.WORKFILES_CONFIG_DIRECTORY_ID
import github.ponyhuang.gimi.data.workfiles.repository.AndroidDocumentTreeGateway
import github.ponyhuang.gimi.data.workfiles.repository.AndroidDocumentSearchGateway
import github.ponyhuang.gimi.data.workfiles.repository.DefaultDocumentSearchRepository
import github.ponyhuang.gimi.data.workfiles.repository.DefaultAppStorageManagementRepository
import github.ponyhuang.gimi.data.workfiles.repository.DocumentSearchGateway
import github.ponyhuang.gimi.data.workfiles.repository.DocumentDirectoryRepository
import github.ponyhuang.gimi.data.workfiles.repository.DocumentTreeGateway
import github.ponyhuang.gimi.data.workfiles.repository.WorkDirectoryConfigSerializer
import github.ponyhuang.gimi.data.workfiles.repository.WorkDirectoryConfigStore
import github.ponyhuang.gimi.data.workfiles.workFilesConfigDirectorySpec
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import github.ponyhuang.gimi.domain.workfiles.repository.AppStorageManagementRepository
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkFilesModule {
    @Binds
    @Singleton
    abstract fun bindWorkDirectoryRepository(
        implementation: DocumentDirectoryRepository,
    ): WorkDirectoryRepository

    @Binds
    @Singleton
    abstract fun bindDocumentTreeGateway(
        implementation: AndroidDocumentTreeGateway,
    ): DocumentTreeGateway

    @Binds
    @Singleton
    abstract fun bindDocumentSearchGateway(
        implementation: AndroidDocumentSearchGateway,
    ): DocumentSearchGateway

    @Binds
    @Singleton
    abstract fun bindDocumentSearchRepository(
        implementation: DefaultDocumentSearchRepository,
    ): DocumentSearchRepository

    @Binds
    @Singleton
    abstract fun bindAppStorageManagementRepository(
        implementation: DefaultAppStorageManagementRepository,
    ): AppStorageManagementRepository

    companion object {
        @Provides
        @IntoSet
        fun provideWorkFilesConfigDirectorySpec(): ManagedDirectorySpec =
            workFilesConfigDirectorySpec

        @Provides
        @Singleton
        fun provideWorkDirectoryConfigStore(
            storageRegistry: StorageRegistry,
        ): WorkDirectoryConfigStore = WorkDirectoryConfigStore(
            DataStoreFactory.create(
                serializer = WorkDirectoryConfigSerializer,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = {
                    File(
                        storageRegistry.resolve(WORKFILES_CONFIG_DIRECTORY_ID, create = true),
                        "work-directories.json",
                    )
                },
            ),
        )
    }
}
