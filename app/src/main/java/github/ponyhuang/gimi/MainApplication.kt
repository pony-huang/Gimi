package github.ponyhuang.gimi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.recommendation.runtime.RecommendationStartupInitializer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {
    @Inject lateinit var backgroundAppWindowHost: github.ponyhuang.gimi.mobileuse.BackgroundAppWindowHost
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var recommendationStartupInitializer: RecommendationStartupInitializer
    @Inject lateinit var appUpdateRepository: AppUpdateRepository
    @Inject lateinit var appLogRecorder: github.ponyhuang.gimi.data.logging.AppLogRecorder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appLogRecorder.start(appScope)
        backgroundAppWindowHost.start()
        appScope.launch {
            recommendationStartupInitializer.reconcile()
        }
        // 每次冷启动后台静默检查更新：不弹任何 UI，发现新版本只点亮设置入口红点；
        // 网络/限流错误在仓库内静默吞掉。进程创建才跑一次，从后台切回不重复。
        appScope.launch {
            appUpdateRepository.checkForUpdate(manual = false)
        }
    }
}
