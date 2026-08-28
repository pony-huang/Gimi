package github.ponyhuang.gimi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import github.ponyhuang.gimi.data.recommendation.RecommendationPreferences
import javax.inject.Inject

@HiltAndroidApp
class AsssistantaiApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var recommendationPreferences: RecommendationPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        recommendationPreferences.reconcileWork()
    }
}
