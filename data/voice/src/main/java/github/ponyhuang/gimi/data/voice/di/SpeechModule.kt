package github.ponyhuang.gimi.data.voice.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.voice.playback.AndroidSpeechPlaybackRepository
import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.data.voice.repository.DefaultSpeechRecognitionRepository
import github.ponyhuang.gimi.data.voice.repository.DefaultSpeechSynthesisRepository
import github.ponyhuang.gimi.data.voice.repository.DefaultTtsVoiceRepository
import github.ponyhuang.gimi.data.voice.repository.RealtimeVoiceModePreferences
import github.ponyhuang.gimi.data.voice.repository.SimpleChatPromptPreferences
import github.ponyhuang.gimi.data.voice.repository.SpeechSettingsPreferences
import github.ponyhuang.gimi.data.voice.runtime.AndroidRealtimeVoiceCallForeground
import github.ponyhuang.gimi.data.voice.runtime.InMemoryRealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallForeground
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceModeRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Singleton

/**
 * 语音业务层绑定：仓库、呼叫服务与能力解析表。
 * 厂商适配器（ASR/TTS）的绑定在 `:data:modelaccess` 的
 * [github.ponyhuang.gimi.data.modelaccess.di.ModelAccessModule]。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {

    @Binds
    abstract fun bindRealtimeVoiceRepository(
        implementation: github.ponyhuang.gimi.data.voice.repository.DefaultRealtimeVoiceRepository,
    ): github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceRepository

    @Binds
    @Singleton
    abstract fun bindRealtimeVoiceCallController(
        implementation: InMemoryRealtimeVoiceCallController,
    ): RealtimeVoiceCallController

    @Binds
    @Singleton
    abstract fun bindRealtimeVoiceCallForeground(
        implementation: AndroidRealtimeVoiceCallForeground,
    ): RealtimeVoiceCallForeground

    @Binds
    @Singleton
    abstract fun bindSpeechRecognitionRepository(
        implementation: DefaultSpeechRecognitionRepository,
    ): SpeechRecognitionRepository

    @Binds
    @Singleton
    abstract fun bindSpeechSynthesisRepository(
        implementation: DefaultSpeechSynthesisRepository,
    ): SpeechSynthesisRepository

    @Binds
    @Singleton
    abstract fun bindSpeechPlaybackRepository(
        implementation: AndroidSpeechPlaybackRepository,
    ): SpeechPlaybackRepository

    @Binds
    @Singleton
    abstract fun bindSpeechSettingsRepository(
        implementation: SpeechSettingsPreferences,
    ): SpeechSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSimpleChatPromptRepository(
        implementation: SimpleChatPromptPreferences,
    ): SimpleChatPromptRepository

    @Binds
    @Singleton
    abstract fun bindRealtimeVoiceModeRepository(
        implementation: RealtimeVoiceModePreferences,
    ): RealtimeVoiceModeRepository

    @Binds
    @Singleton
    abstract fun bindTtsVoiceRepository(
        implementation: DefaultTtsVoiceRepository,
    ): TtsVoiceRepository

    @Binds
    @Singleton
    abstract fun bindVoiceCapabilityResolver(
        implementation: VoiceCapabilityRegistry,
    ): VoiceCapabilityResolver
}
