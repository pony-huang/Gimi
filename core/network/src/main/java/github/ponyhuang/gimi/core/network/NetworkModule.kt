package github.ponyhuang.gimi.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.StorageMaintenanceHandler
import github.ponyhuang.gimi.core.storage.StorageRegistry
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpCache(
        registry: StorageRegistry,
    ): Cache = Cache(
        directory = registry.resolve(NetworkStorage.HTTP_CACHE_ID, create = true),
        maxSize = HTTP_CACHE_BYTES,
    )

    @Provides
    @IntoSet
    fun provideHttpCacheDirectorySpec(): ManagedDirectorySpec = NetworkStorage.HttpCache

    @Provides
    @IntoSet
    fun provideNetworkStorageMaintenanceHandler(cache: Cache): StorageMaintenanceHandler =
        object : StorageMaintenanceHandler {
            override val owner = "network"

            override suspend fun clear(
                spec: ManagedDirectorySpec,
                directory: java.io.File,
            ): Boolean = runCatching {
                cache.evictAll()
                true
            }.getOrDefault(false)
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(cache: Cache): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .cache(cache)
        .build()

    private const val HTTP_CACHE_BYTES = 50L * 1024L * 1024L
}
