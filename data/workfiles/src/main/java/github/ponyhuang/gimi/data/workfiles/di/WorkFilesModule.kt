package github.ponyhuang.gimi.data.workfiles.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.workfiles.repository.DocumentDirectoryRepository
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkFilesModule {
    @Binds
    @Singleton
    abstract fun bindWorkDirectoryRepository(
        implementation: DocumentDirectoryRepository,
    ): WorkDirectoryRepository
}
