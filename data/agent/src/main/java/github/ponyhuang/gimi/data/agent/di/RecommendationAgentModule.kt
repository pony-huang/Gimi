package github.ponyhuang.gimi.data.agent.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.agent.recommendation.AgentRecommendationCapabilitySource
import github.ponyhuang.gimi.data.agent.recommendation.AgentRecommendationGenerator
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationCapabilitySource
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationGenerator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecommendationAgentModule {
    @Binds
    @Singleton
    abstract fun bindRecommendationGenerator(
        implementation: AgentRecommendationGenerator,
    ): RecommendationGenerator

    @Binds
    @Singleton
    abstract fun bindRecommendationCapabilitySource(
        implementation: AgentRecommendationCapabilitySource,
    ): RecommendationCapabilitySource
}
