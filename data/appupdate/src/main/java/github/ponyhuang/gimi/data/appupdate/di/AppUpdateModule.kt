package github.ponyhuang.gimi.data.appupdate.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.appupdate.DefaultAppUpdateRepository
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        implementation: DefaultAppUpdateRepository,
    ): AppUpdateRepository
}
