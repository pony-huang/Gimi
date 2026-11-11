package github.ponyhuang.gimi.data.voice.di

import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.core.network.SpeechWebSocketClient
import github.ponyhuang.gimi.data.voice.playback.AndroidSpeechPlaybackRepository
import github.ponyhuang.gimi.data.voice.remote.DASHSCOPE_SERVICE_ID
import github.ponyhuang.gimi.data.voice.remote.DashScopeOneShotRecognizer
import github.ponyhuang.gimi.data.voice.remote.DashScopeRecognitionProtocol
import github.ponyhuang.gimi.data.voice.remote.DashScopeStreamingRecognizer
import github.ponyhuang.gimi.data.voice.remote.DashScopeSynthesizer
import github.ponyhuang.gimi.data.voice.remote.DashScopeVoiceProvider
import github.ponyhuang.gimi.data.voice.remote.MINIMAX_SERVICE_ID
import github.ponyhuang.gimi.data.voice.remote.MiMoAudioRecognizer
import github.ponyhuang.gimi.data.voice.remote.MiMoSpeechProtocol
import github.ponyhuang.gimi.data.voice.remote.MiMoSynthesizer
import github.ponyhuang.gimi.data.voice.remote.MiMoVoiceProvider
import github.ponyhuang.gimi.data.voice.remote.MinimaxBidiSynthesizer
import github.ponyhuang.gimi.data.voice.remote.MinimaxRecognizer
import github.ponyhuang.gimi.data.voice.remote.MinimaxSpeechProtocol
import github.ponyhuang.gimi.data.voice.remote.MinimaxSynthesizer
import github.ponyhuang.gimi.data.voice.remote.MinimaxVoiceProvider
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleAudioRecognizer
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleChatProtocol
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleSynthesizer
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
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import github.ponyhuang.gimi.domain.voice.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceProvider
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

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
    @IntoSet
    abstract fun bindMiMoVoiceProvider(
        implementation: MiMoVoiceProvider,
    ): TtsVoiceProvider

    @Binds
    @IntoSet
    abstract fun bindDashScopeVoiceProvider(
        implementation: DashScopeVoiceProvider,
    ): TtsVoiceProvider

    @Binds
    @IntoSet
    abstract fun bindOpenAiCompatibleConnectionProbe(
        implementation: github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleConnectionProbe,
    ): SpeechConnectionProbe

    @Binds
    @Singleton
    abstract fun bindVoiceCapabilityResolver(
        implementation: VoiceCapabilityRegistry,
    ): VoiceCapabilityResolver

    @Binds
    @IntoSet
    abstract fun bindDashScopeSynthesizer(
        implementation: DashScopeSynthesizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindDashScopeOneShotRecognizer(
        implementation: DashScopeOneShotRecognizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindDashScopeStreamingRecognizer(
        implementation: DashScopeStreamingRecognizer,
    ): github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindMinimaxSynthesizer(
        implementation: MinimaxSynthesizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindMinimaxRecognizer(
        implementation: MinimaxRecognizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindMiMoSynthesizer(
        implementation: MiMoSynthesizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindMiMoAudioRecognizer(
        implementation: MiMoAudioRecognizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindOpenAiSpeechRecognizer(
        implementation: github.ponyhuang.gimi.data.voice.remote.OpenAiSpeechRecognizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindOpenAiSpeechSynthesizer(
        implementation: github.ponyhuang.gimi.data.voice.remote.OpenAiSpeechSynthesizer,
    ): github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer

    companion object {
        /**
         * 语音服务的 WebSocket 客户端。
         *
         * 共享的 HTTP 客户端带 60 秒读超时与整体调用超时，长连接会被它掐断，
         * 因此这里单独放宽读超时，改由 ping 维持连接。
         */
        @Provides
        @Singleton
        @SpeechWebSocketClient
        fun provideSpeechWebSocketClient(
            okHttpClient: OkHttpClient,
        ): OkHttpClient = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        @Provides
        @Singleton
        fun provideDashScopeRecognitionProtocol(
            @SpeechWebSocketClient speechWebSocketClient: OkHttpClient,
        ): DashScopeRecognitionProtocol = DashScopeRecognitionProtocol(speechWebSocketClient)

        @Provides
        @Singleton
        fun provideDashScopeSynthesizer(
            @SpeechWebSocketClient speechWebSocketClient: OkHttpClient,
        ): DashScopeSynthesizer = DashScopeSynthesizer(speechWebSocketClient)

        @Provides
        @Singleton
        fun provideMinimaxSpeechProtocol(
            okHttpClient: OkHttpClient,
        ): MinimaxSpeechProtocol = MinimaxSpeechProtocol(okHttpClient, Gson())

        @Provides
        @Singleton
        fun provideMiMoSpeechProtocol(
            okHttpClient: OkHttpClient,
        ): MiMoSpeechProtocol = MiMoSpeechProtocol(okHttpClient, Gson())

        /**
         * MiniMax 双向流式语音合成。
         *
         * 走 WebSocket 专用客户端：共享的 HTTP 客户端带读超时与整体调用超时，
         * 长连接会被它掐断，而这个会话要跨整轮对话保持连接。
         */
        @Provides
        @Singleton
        @IntoSet
        fun provideMinimaxBidiSynthesizer(
            @SpeechWebSocketClient speechWebSocketClient: OkHttpClient,
        ): github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechSynthesizer =
            MinimaxBidiSynthesizer(speechWebSocketClient)

        @Provides
        @Singleton
        fun provideOpenAiCompatibleChatProtocol(
            okHttpClient: OkHttpClient,
        ): OpenAiCompatibleChatProtocol = OpenAiCompatibleChatProtocol(okHttpClient, Gson())

        @Provides
        @Singleton
        @IntoSet
        fun provideOpenAiCompatibleSynthesizer(
            protocol: OpenAiCompatibleChatProtocol,
        ): github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer =
            OpenAiCompatibleSynthesizer(protocol)

        @Provides
        @Singleton
        @IntoSet
        fun provideOpenAiCompatibleAudioRecognizer(
            protocol: OpenAiCompatibleChatProtocol,
        ): github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer =
            OpenAiCompatibleAudioRecognizer(protocol)

        @Provides
        @Singleton
        @IntoSet
        fun provideMinimaxVoiceProvider(
            okHttpClient: OkHttpClient,
        ): TtsVoiceProvider = MinimaxVoiceProvider(okHttpClient)
    }
}
