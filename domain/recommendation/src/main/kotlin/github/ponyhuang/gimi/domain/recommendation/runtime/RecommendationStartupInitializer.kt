package github.ponyhuang.gimi.domain.recommendation.runtime

/**
 * 由宿主在 [Application.onCreate] 调用的窄接口；将 app 从具体
 * `RecommendationPreferences`/`RecommendationWork` 协调细节中解耦，
 * 后续接入 startup initializer（`androidx.startup` 或类似机制）时无需改动 app。
 */
interface RecommendationStartupInitializer {
    /** 协调一次性恢复调度（保证已有 WorkRequest 与最新设置一致）。 */
    suspend fun reconcile()
}