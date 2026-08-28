package github.ponyhuang.gimi.data.recommendation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationGenerationInput
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSettings
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationSnapshot
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationCapabilitySource
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationContextSource
import github.ponyhuang.gimi.domain.recommendation.repository.RecommendationGenerator
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** 推荐刷新对于后台调度器的稳定结果分类。 */
enum class RecommendationRefreshOutcome {
    Success,
    Retry,
    Failure,
}

/** 串联 Agent 空闲门、能力、只读上下文、模型生成和快照提交。 */
@Singleton
class RecommendationRefresher(
    private val generator: RecommendationGenerator,
    private val capabilities: RecommendationCapabilitySource,
    private val context: RecommendationContextSource,
    private val preferences: RecommendationPreferences,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val nowEpochMillis: () -> Long,
) {
    @Inject
    constructor(
        generator: RecommendationGenerator,
        capabilities: RecommendationCapabilitySource,
        context: RecommendationContextSource,
        preferences: RecommendationPreferences,
        runWhenAgentIdle: RunWhenAgentIdleUseCase,
    ) : this(
        generator = generator,
        capabilities = capabilities,
        context = context,
        preferences = preferences,
        runWhenAgentIdle = runWhenAgentIdle,
        nowEpochMillis = System::currentTimeMillis,
    )

    suspend fun refresh(): RecommendationRefreshOutcome {
        return when (
            val result = runWhenAgentIdle {
                preferences.markRefreshing()
                val generated = generator.generate(
                    RecommendationGenerationInput(
                        systemInstruction = "",
                        capabilities = capabilities.capabilities(),
                        context = context.currentContext(),
                    ),
                )
                preferences.saveSnapshot(RecommendationSnapshot(generated, nowEpochMillis()))
            }
        ) {
            is AgentMutationResult.Applied -> RecommendationRefreshOutcome.Success
            AgentMutationResult.BlockedByActiveAgent -> RecommendationRefreshOutcome.Retry
        }
    }

    suspend fun refreshSafely(): RecommendationRefreshOutcome = try {
        refresh()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        preferences.markFailed(error.message ?: "Recommendation update failed")
        when (error) {
            is IOException -> RecommendationRefreshOutcome.Retry
            is IllegalArgumentException,
            is IllegalStateException,
            -> RecommendationRefreshOutcome.Failure
            else -> RecommendationRefreshOutcome.Retry
        }
    }
}

/** WorkManager 的唯一任务实现。 */
@Singleton
class WorkManagerRecommendationScheduler @Inject constructor(
    private val workManager: WorkManager,
) : RecommendationWorkScheduler {
    override fun reconcile(settings: RecommendationSettings, hasSnapshot: Boolean) {
        if (!settings.enabled) {
            cancel()
            return
        }
        schedulePeriodic(settings.intervalHours)
        if (!hasSnapshot) enqueueImmediate()
    }

    override fun schedulePeriodic(intervalHours: Int) {
        require(intervalHours in RecommendationSettings.SUPPORTED_INTERVAL_HOURS)
        val request = PeriodicWorkRequestBuilder<RecommendationRefreshWorker>(
            intervalHours.toLong(),
            TimeUnit.HOURS,
        ).setConstraints(WORK_CONSTRAINTS).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun enqueueImmediate() {
        val request = OneTimeWorkRequestBuilder<RecommendationRefreshWorker>()
            .setConstraints(WORK_CONSTRAINTS)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
    }

    companion object {
        const val PERIODIC_WORK_NAME: String = "agent-recommendations-periodic"
        const val IMMEDIATE_WORK_NAME: String = "agent-recommendations-immediate"

        private val WORK_CONSTRAINTS = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    }
}

/** 执行一次推荐刷新并把稳定结果映射为 WorkManager 语义。 */
@HiltWorker
class RecommendationRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val refresher: RecommendationRefresher,
    private val preferences: RecommendationPreferences,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        if (!preferences.state.value.settings.enabled) return Result.success()
        return when (refresher.refreshSafely()) {
            RecommendationRefreshOutcome.Success -> Result.success()
            RecommendationRefreshOutcome.Retry -> Result.retry()
            RecommendationRefreshOutcome.Failure -> Result.failure()
        }
    }
}
