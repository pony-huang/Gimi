package github.ponyhuang.gimi.data.memory.di

import android.content.Context
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.appsearch.AppSearchMemoryService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.memory.DefaultMemoryRuntimeStatus
import github.ponyhuang.gimi.data.memory.KeystoreMemorySettingsStorage
import github.ponyhuang.gimi.data.memory.Mem0MemoryService
import github.ponyhuang.gimi.data.memory.Mem0ApiClient
import github.ponyhuang.gimi.data.memory.Mem0MemoryManagementRepositoryImpl
import github.ponyhuang.gimi.data.memory.MemorySettingsStorage
import github.ponyhuang.gimi.data.memory.RoutingMemoryService
import github.ponyhuang.gimi.data.memory.SecureMemorySettingsRepository
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import github.ponyhuang.gimi.domain.memory.repository.Mem0MemoryManagementRepository
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSettingsStorage(implementation: KeystoreMemorySettingsStorage): MemorySettingsStorage

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: SecureMemorySettingsRepository): MemorySettingsRepository

    @Binds
    @Singleton
    abstract fun bindRuntimeStatus(implementation: DefaultMemoryRuntimeStatus): MemoryRuntimeStatus

    @Binds
    @Singleton
    abstract fun bindMem0MemoryManagementRepository(
        implementation: Mem0MemoryManagementRepositoryImpl,
    ): Mem0MemoryManagementRepository
}

@Module
@InstallIn(SingletonComponent::class)
object MemoryServiceModule {
    @Provides
    @Singleton
    @Mem0HttpClient
    fun provideMem0HttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideMem0MemoryService(
        api: Mem0ApiClient,
        runtimeStatus: MemoryRuntimeStatus,
    ): Mem0MemoryService = Mem0MemoryService(
        api = api,
        runtimeStatus = runtimeStatus,
    )

    @Provides
    @Singleton
    fun provideMem0MemoryManagementRepository(
        api: Mem0ApiClient,
    ): Mem0MemoryManagementRepositoryImpl = Mem0MemoryManagementRepositoryImpl(api)

    @Provides
    @Singleton
    fun provideMem0ApiClient(
        @Mem0HttpClient httpClient: OkHttpClient,
        settingsRepository: MemorySettingsRepository,
    ): Mem0ApiClient = Mem0ApiClient(
        httpClient = httpClient,
        settingsRepository = settingsRepository,
    )

    @Provides
    @Singleton
    fun provideMemoryService(
        @ApplicationContext context: Context,
        settingsRepository: MemorySettingsRepository,
        mem0MemoryService: Mem0MemoryService,
    ): MemoryService = RoutingMemoryService(
        settingsRepository = settingsRepository,
        localMemoryService = AppSearchMemoryService.fromContext(context),
        mem0MemoryService = mem0MemoryService,
    )
}
