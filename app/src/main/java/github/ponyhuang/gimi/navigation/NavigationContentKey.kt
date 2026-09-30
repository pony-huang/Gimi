package github.ponyhuang.gimi.navigation

import androidx.navigation3.runtime.NavKey

/** 区分跨 feature 的同名 destination，并保留同类型 destination 的参数身份。 */
internal fun NavKey.navigationContentKey(): String {
    // Navigation 默认的 toString() 会让多个 Settings destination 共享 Scene key。
    return "${this::class.qualifiedName}:$this"
}
