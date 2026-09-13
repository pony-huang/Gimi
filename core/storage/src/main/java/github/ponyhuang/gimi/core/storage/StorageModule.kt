package github.ponyhuang.gimi.core.storage

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {
    @Binds
    abstract fun bindAppDirectoryResolver(
        implementation: AndroidAppDirectoryResolver,
    ): AppDirectoryResolver
}
