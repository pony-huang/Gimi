package github.ponyhuang.gimi.data.workfiles.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.workfiles.repository.AndroidDocumentSearchGateway
import github.ponyhuang.gimi.data.workfiles.repository.DefaultDocumentSearchRepository
import github.ponyhuang.gimi.data.workfiles.repository.DocumentSearchGateway
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkFilesModule {
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
}
