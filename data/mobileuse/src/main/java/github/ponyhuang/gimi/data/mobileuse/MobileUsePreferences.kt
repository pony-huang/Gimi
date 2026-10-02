package github.ponyhuang.gimi.data.mobileuse

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 持久化后台操作开关；未设置或数据损坏时默认关闭，系统授权不受影响。 */
@Singleton
class MobileUsePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("mobile_use_preferences", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(
        try {
            preferences.getBoolean("enabled", false)
        } catch (_: ClassCastException) {
            false
        },
    )
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableEnabled.value = enabled
    }
}
