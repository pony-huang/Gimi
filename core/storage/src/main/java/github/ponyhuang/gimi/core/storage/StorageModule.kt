package github.ponyhuang.gimi.core.storage

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    abstract fun bindAppDirectoryResolver(
        implementation: AndroidAppDirectoryResolver,
    ): AppDirectoryResolver

    @Multibinds
    abstract fun managedDirectorySpecs(): Set<ManagedDirectorySpec>

    @Multibinds
    abstract fun storageMaintenanceHandlers(): Set<StorageMaintenanceHandler>

    companion object {
        @Provides
        @Singleton
        fun provideManagedStorageService(
            registry: StorageRegistry,
            handlers: Set<@JvmSuppressWildcards StorageMaintenanceHandler>,
        ): ManagedStorageService = ManagedStorageService(
            registry = registry,
            handlers = handlers,
            ioDispatcher = Dispatchers.IO,
        )
    }
}
