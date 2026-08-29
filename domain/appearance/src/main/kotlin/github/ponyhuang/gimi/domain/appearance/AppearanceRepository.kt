package github.ponyhuang.gimi.domain.appearance

import kotlinx.coroutines.flow.StateFlow

/**
 * 应用级外观设置（夜间模式等）。`null` 表示跟随系统（官方文档推荐的默认项）。
 * 用户一旦拨动开关就写入明确的浅/深偏好，之后不再随系统变化。
 */
interface AppearanceRepository {
    val darkThemeOverride: StateFlow<Boolean?>

    fun setDarkThemeOverride(enabled: Boolean)
}