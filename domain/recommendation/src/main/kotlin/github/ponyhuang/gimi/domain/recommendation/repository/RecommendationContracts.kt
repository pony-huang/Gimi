package github.ponyhuang.gimi.domain.recommendation.repository

import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCapability
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationContext
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationState
import kotlinx.coroutines.flow.StateFlow

/** 全局推荐状态、设置和刷新入口。 */
interface RecommendationRepository {
    val state: StateFlow<RecommendationState>

    fun setEnabled(enabled: Boolean)
    fun setIntervalHours(intervalHours: Int)
    fun requestRefresh()
}

/** 由模型 provider 实现的无工具推荐生成边界。 */
interface RecommendationGenerator {
    suspend fun generate(input: RecommendationGenerationInput): List<AgentRecommendation>
}

/** 汇总当前可推荐的工具能力，不执行任何工具。 */
interface RecommendationCapabilitySource {
    suspend fun capabilities(): List<RecommendationCapability>
}

/** 采集单次生成所需、无需交互且已授权的只读上下文。 */
interface RecommendationContextSource {
    suspend fun currentContext(): RecommendationContext
}
