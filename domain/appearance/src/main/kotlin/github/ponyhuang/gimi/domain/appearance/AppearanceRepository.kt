package github.ponyhuang.gimi.domain.appearance

import kotlinx.coroutines.flow.StateFlow

/**
 * 应用级外观设置（夜间模式等）。默认 [ThemeMode.SYSTEM] 跟随系统；用户手动选择
 * [ThemeMode.LIGHT]/[ThemeMode.DARK] 后锁定，仍可随时切回 [ThemeMode.SYSTEM] 恢复自动跟随。
 */
interface AppearanceRepository {
    val themeMode: StateFlow<ThemeMode>

    fun setThemeMode(mode: ThemeMode)
}