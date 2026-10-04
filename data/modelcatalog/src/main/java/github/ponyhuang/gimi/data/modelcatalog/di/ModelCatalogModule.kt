package github.ponyhuang.gimi.data.modelcatalog.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.core.common.coroutine.IoDispatcher
import github.ponyhuang.gimi.data.modelcatalog.LLMModelRoomDatabase
import github.ponyhuang.gimi.data.modelcatalog.ModelServiceRepository
import github.ponyhuang.gimi.data.modelcatalog.official.DefaultOfficialToolFunctionCatalog
import github.ponyhuang.gimi.data.modelcatalog.official.kimi.KimiFormulaCache
import github.ponyhuang.gimi.data.modelcatalog.remote.OpenAiCompatibleModelServiceGateway
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.KimiFormulaSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelServiceRemoteGateway
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class ModelCatalogBindingsModule {
    @Binds
    abstract fun bindOfficialToolFunctionCatalog(
        implementation: DefaultOfficialToolFunctionCatalog,
    ): OfficialToolFunctionCatalog

    @Binds
    abstract fun bindKimiFormulaSource(implementation: KimiFormulaCache): KimiFormulaSource

    @Binds
    @Singleton
    abstract fun bindModelCatalogRepository(
        implementation: ModelServiceRepository,
    ): ModelCatalogRepository

    @Binds
    @Singleton
    abstract fun bindAgentModelConfigurationSource(
        implementation: ModelServiceRepository,
    ): AgentModelConfigurationSource

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
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
