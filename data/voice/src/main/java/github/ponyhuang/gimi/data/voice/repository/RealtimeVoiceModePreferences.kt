package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceModeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 使用应用私有偏好持久化上次使用的语音对话模式。
 * 读取容忍损坏数据：枚举名无法解析时回落默认的轻聊模式，不阻塞语音功能。
 */
@Singleton
class RealtimeVoiceModePreferences @Inject constructor(
    @ApplicationContext context: Context,
) : RealtimeVoiceModeRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableLastMode = MutableStateFlow(readLastMode())

    override val lastMode: StateFlow<RealtimeVoiceMode> = mutableLastMode.asStateFlow()

    override fun setLastMode(mode: RealtimeVoiceMode) {
        if (mutableLastMode.value == mode) return
        mutableLastMode.value = mode
        preferences.edit { putString(LAST_MODE_KEY, mode.name) }
    }

    private fun readLastMode(): RealtimeVoiceMode = preferences.getString(LAST_MODE_KEY, null)
        ?.let { raw -> runCatching { RealtimeVoiceMode.valueOf(raw) }.getOrNull() }
        ?: DEFAULT_MODE

    private companion object {
        const val PREFERENCES_NAME = "realtime_voice_preferences"
        const val LAST_MODE_KEY = "last_mode"
        val DEFAULT_MODE = RealtimeVoiceMode.Simple
    }
}
