package github.ponyhuang.gimi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import github.ponyhuang.gimi.data.agent.debug.AgentDebugWebServer
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import github.ponyhuang.gimi.domain.recommendation.runtime.RecommendationStartupInitializer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var recommendationStartupInitializer: RecommendationStartupInitializer
    @Inject lateinit var appUpdateRepository: AppUpdateRepository

    /**
     * debug 构建绑定 ADK Development WebServer（PC 浏览器经局域网观察 agent 会话）；
     * release 绑定为空实现，start() 无任何开销，因此不按变体分支。
     */
    @Inject lateinit var agentDebugWebServer: AgentDebugWebServer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            recommendationStartupInitializer.reconcile()
        }
        // 每次冷启动后台静默检查更新：不弹任何 UI，发现新版本只点亮设置入口红点；
        // 网络/限流错误在仓库内静默吞掉。进程创建才跑一次，从后台切回不重复。
        appScope.launch {
            appUpdateRepository.checkForUpdate(manual = false)
        }
        agentDebugWebServer.start()
    }
}