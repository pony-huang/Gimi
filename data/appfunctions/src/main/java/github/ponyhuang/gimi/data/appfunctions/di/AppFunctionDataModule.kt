package github.ponyhuang.gimi.data.appfunctions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.appfunctions.DefaultAppFunctionRepository
import github.ponyhuang.gimi.domain.appfunctions.repository.AppFunctionRepository

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppFunctionDataModule {
    @Binds
    abstract fun bindAppFunctionRepository(
        implementation: DefaultAppFunctionRepository,
    ): AppFunctionRepository
}
