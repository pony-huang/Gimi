package github.ponyhuang.gimi.data.permissions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.permissions.repository.AndroidPermissionRepository
import github.ponyhuang.gimi.domain.permissions.repository.PermissionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {
    @Binds
    @Singleton
    abstract fun bindPermissionRepository(
        implementation: AndroidPermissionRepository,
    ): PermissionRepository
}
