package github.ponyhuang.gimi

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import dagger.hilt.android.HiltAndroidApp
import github.ponyhuang.gimi.appfunctions.AssistantFunctions
import javax.inject.Inject

@HiltAndroidApp
class AsssistantaiApp : Application(), AppFunctionConfiguration.Provider {
    @Inject
    lateinit var assistantFunctions: AssistantFunctions

    override fun onCreate() {
        super.onCreate()
    }

    override val appFunctionConfiguration: AppFunctionConfiguration =
        AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(AssistantFunctions::class.java) { assistantFunctions }
            .build()
}
