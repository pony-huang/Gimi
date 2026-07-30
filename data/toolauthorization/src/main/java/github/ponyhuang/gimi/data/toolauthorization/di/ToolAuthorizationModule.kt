package github.ponyhuang.gimi.data.toolauthorization.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.toolauthorization.ToolAuthorizationPreferences
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolAuthorizationModule {
    @Binds
    @Singleton
    abstract fun bindToolAuthorizationRepository(
        implementation: ToolAuthorizationPreferences,
    ): ToolAuthorizationRepository
}
