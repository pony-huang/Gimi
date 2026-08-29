package github.ponyhuang.gimi.data.appearance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.appearance.AppearancePreferences
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppearanceDataModule {
    @Binds
    @Singleton
    abstract fun bindAppearanceRepository(
        implementation: AppearancePreferences,
    ): AppearanceRepository
}