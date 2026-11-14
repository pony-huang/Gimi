package github.ponyhuang.gimi.data.skills

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 技能启用状态的宿主持久化抽象；宿主实现负责持久化与运行时可见性（读取需线程安全）。 */
internal interface SkillEnabledStore {
    /** 当前被禁用的技能名集合；每次调用返回最新状态，供运行时过滤使用。 */
    fun disabledNames(): Set<String>

    fun setEnabled(name: String, enabled: Boolean)

    /** 技能被删除时清理其启用状态，避免残留孤立项。 */
    fun forget(name: String)
}

/**
 * 基于SharedPreferences的启用状态存储：显式写为 false 的名字视为禁用，
 * 缺失键默认启用，保证升级与首次安装的技能无需迁移即可用。
 */
@Singleton
internal class SharedPreferencesSkillEnabledStore @Inject constructor(
    @ApplicationContext context: Context,
) : SkillEnabledStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val disabled = MutableStateFlow(readDisabledNames())

    override fun disabledNames(): Set<String> = disabled.value

    override fun setEnabled(name: String, enabled: Boolean) {
        synchronized(this) {
            preferences.edit {
                if (enabled) remove(name) else putBoolean(name, false)
            }
            disabled.value = readDisabledNames()
        }
    }

    override fun forget(name: String) {
        synchronized(this) {
            preferences.edit { remove(name) }
            disabled.value = readDisabledNames()
        }
    }

    private fun readDisabledNames(): Set<String> =
        preferences.all.keys.filterNot { preferences.getBoolean(it, true) }.toSet()

    private companion object {
        const val PREFERENCES_NAME = "skills_enabled"
    }
}
