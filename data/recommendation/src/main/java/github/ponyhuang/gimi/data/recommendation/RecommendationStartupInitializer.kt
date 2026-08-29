package github.ponyhuang.gimi.data.recommendation

import github.ponyhuang.gimi.domain.recommendation.runtime.RecommendationStartupInitializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 在 app 启动时调用，封装对 [RecommendationPreferences] 的具体恢复调度。
 *
 * `app` 模块只依赖 [RecommendationStartupInitializer]，未来若切换到 `androidx.startup`，
 * 只需替换这里的实现而不需要再触碰 [github.ponyhuang.gimi.AsssistantaiApp]。
 */
@Singleton
class RecommendationStartupInitializerImpl @Inject constructor(
    private val preferences: RecommendationPreferences,
) : RecommendationStartupInitializer {
    override suspend fun reconcile() {
        preferences.reconcileWork()
    }
}