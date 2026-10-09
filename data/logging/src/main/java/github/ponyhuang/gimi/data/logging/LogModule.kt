package github.ponyhuang.gimi.data.logging

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.logging.LogRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LogModule {
    @Binds
    @Singleton
    abstract fun bindLogRepository(implementation: AndroidLogRepository): LogRepository
}
