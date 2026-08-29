package github.ponyhuang.gimi.data.recommendation.di

import dagger.Binds
import dagger.multibindings.IntoSet
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.recommendation.RecommendationPreferences
import github.ponyhuang.gimi.data.recommendation.AndroidRecommendationContextContributor
import github.ponyhuang.gimi.data.recommendation.RecommendationContextCollector
import github.ponyhuang.gimi.data.recommendation.RecommendationContextContributor
import github.ponyhuang.gimi.data.recommendation.RecommendationStartupInitializerImpl
import github.ponyhuang.gimi.data.recommendation.RecommendationWorkScheduler
import github.ponyhuang.gimi.data.recommendation.WorkManagerRecommendationScheduler
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationContextSource
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationRepository
import github.ponyhuang.gimi.domain.recommendation.runtime.RecommendationStartupInitializer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecommendationDataModule {
    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(
        implementation: RecommendationPreferences,
    ): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationContextSource(
        implementation: RecommendationContextCollector,
    ): RecommendationContextSource

    @Binds
    @IntoSet
    abstract fun bindAndroidContextContributor(
        implementation: AndroidRecommendationContextContributor,
    ): RecommendationContextContributor

    @Binds
    @Singleton
    abstract fun bindRecommendationWorkScheduler(
        implementation: WorkManagerRecommendationScheduler,
    ): RecommendationWorkScheduler

    @Binds
    @Singleton
    abstract fun bindRecommendationStartupInitializer(
        implementation: RecommendationStartupInitializerImpl,
    ): RecommendationStartupInitializer
}
