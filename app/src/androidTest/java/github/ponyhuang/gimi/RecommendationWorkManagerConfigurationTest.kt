package github.ponyhuang.gimi

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证应用初始化的 WorkManager 使用 Hilt 工厂，而不是反射回退工厂。 */
@RunWith(AndroidJUnit4::class)
class RecommendationWorkManagerConfigurationTest {
    @Test
    fun workManagerUsesHiltWorkerFactory() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertTrue(
            WorkManager.getInstance(context).configuration.workerFactory is HiltWorkerFactory,
        )
    }
}
