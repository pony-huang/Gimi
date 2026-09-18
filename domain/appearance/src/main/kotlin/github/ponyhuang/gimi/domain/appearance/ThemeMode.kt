package github.ponyhuang.gimi.domain.appearance

/**
 * 应用级夜间模式偏好（三态）。
 *
 * @property SYSTEM 跟随系统的深色模式设置，系统切换时应用自动跟随（官方文档推荐的默认项）。
 * @property LIGHT 锁定浅色模式，不再随系统变化。
 * @property DARK 锁定深色模式，不再随系统变化。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
