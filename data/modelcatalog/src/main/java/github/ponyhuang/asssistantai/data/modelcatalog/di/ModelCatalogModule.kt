package github.ponyhuang.asssistantai.data.modelcatalog.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.core.common.coroutine.IoDispatcher
import github.ponyhuang.asssistantai.core.database.destructiveForPrototype
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.data.LLMModelRoomDatabase
import github.ponyhuang.asssistantai.data.modelcatalog.remote.OpenAiCompatibleModelServiceGateway
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelServiceRemoteGateway
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelCatalogBindingsModule {
    @Binds
    @Singleton
    abstract fun bindModelCatalogRepository(
        implementation: ModelServiceRepository,
    ): ModelCatalogRepository

    @Binds
    @Singleton
    abstract fun bindModelServiceRemoteGateway(
        implementation: OpenAiCompatibleModelServiceGateway,
    ): ModelServiceRemoteGateway
}

@Module
@InstallIn(SingletonComponent::class)
object ModelCatalogInfrastructureModule {
    @Provides
    @Singleton
    fun provideModelServiceDatabase(
        @ApplicationContext context: Context,
    ): LLMModelRoomDatabase = Room.databaseBuilder(
        context,
        LLMModelRoomDatabase::class.java,
        LLMModelRoomDatabase.DATABASE_NAME,
    ).destructiveForPrototype().build()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
