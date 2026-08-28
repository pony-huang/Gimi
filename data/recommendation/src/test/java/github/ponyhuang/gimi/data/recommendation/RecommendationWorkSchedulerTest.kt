package github.ponyhuang.gimi.data.recommendation

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class RecommendationWorkSchedulerTest {
    @Test
    fun usesUniquePeriodicAndImmediateWorkNames() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val scheduler = WorkManagerRecommendationScheduler(workManager)

        scheduler.schedulePeriodic(2)
        scheduler.enqueueImmediate()

        verify {
            workManager.enqueueUniquePeriodicWork(
                WorkManagerRecommendationScheduler.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                any<PeriodicWorkRequest>(),
            )
        }
        verify {
            workManager.enqueueUniqueWork(
                WorkManagerRecommendationScheduler.IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun cancelStopsBothUniqueWorkChains() {
        val workManager = mockk<WorkManager>(relaxed = true)
        val scheduler = WorkManagerRecommendationScheduler(workManager)

        scheduler.cancel()

        verify { workManager.cancelUniqueWork(WorkManagerRecommendationScheduler.PERIODIC_WORK_NAME) }
        verify { workManager.cancelUniqueWork(WorkManagerRecommendationScheduler.IMMEDIATE_WORK_NAME) }
    }
}
