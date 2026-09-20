package github.ponyhuang.gimi.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds
    @Singleton
    abstract fun bindAppNotificationManager(
        implementation: AndroidAppNotificationManager,
    ): AppNotificationManager
}
