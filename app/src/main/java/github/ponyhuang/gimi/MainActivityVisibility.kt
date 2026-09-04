package github.ponyhuang.gimi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 供语音前台服务判断主应用是否可直接承载助手界面。 */
object MainActivityVisibility {
    private val mutableForeground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = mutableForeground.asStateFlow()

    fun setForeground(value: Boolean) {
        mutableForeground.value = value
    }
}
