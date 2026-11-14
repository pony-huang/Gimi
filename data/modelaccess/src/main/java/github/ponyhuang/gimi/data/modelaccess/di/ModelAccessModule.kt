package github.ponyhuang.gimi.data.modelaccess.di

import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.core.network.SpeechWebSocketClient
import github.ponyhuang.gimi.data.modelaccess.deepseek.speech.DeepSeekSpeechRecognizer
import github.ponyhuang.gimi.data.modelaccess.deepseek.speech.DeepSeekSpeechSynthesizer
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeOneShotRecognizer
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeRecognitionProtocol
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeStreamingRecognizer
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeSynthesizer
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeVoiceProvider
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoAudioRecognizer
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoSpeechProtocol
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoSynthesizer
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoVoiceProvider
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxBidiSynthesizer
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxRecognizer
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxSpeechProtocol
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxSynthesizer
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxVoiceProvider
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleAudioRecognizer
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleChatProtocol
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleConnectionProbe
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleSynthesizer
import github.ponyhuang.gimi.data.modelaccess.openai.speech.OpenAiSpeechRecognizer
import github.ponyhuang.gimi.data.modelaccess.openai.speech.OpenAiSpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.SpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechRecognizer
import github.ponyhuang.gimi.domain.voice.repository.StreamingSpeechSynthesizer
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceProvider
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 厂商语音适配器（ASR/TTS）绑定：与聊天/实时/级联模型适配共同构成厂商模型接入层。
 * 实现一律实现 `:domain:voice` 接口并以 @IntoSet 贡献，由 `:data:voice` 的
 * VoiceCapabilityRegistry 按 (serviceId, capability) 解析；模块内组合（级联/半级联）
 * 同样只允许依赖 domain 接口，不得直连兄弟子包的实现类。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModelAccessModule {

    @Binds
    @IntoSet
    abstract fun bindDashScopeVoiceProvider(
        implementation: DashScopeVoiceProvider,
    ): TtsVoiceProvider

    @Binds
    @IntoSet
    abstract fun bindMiMoVoiceProvider(
        implementation: MiMoVoiceProvider,
    ): TtsVoiceProvider

    @Binds
    @Singleton
    abstract fun bindVoiceCatalogResolver(
        implementation: github.ponyhuang.gimi.data.modelaccess.shared.VoiceCatalog,
    ): github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver

    @Binds
    @IntoSet
    abstract fun bindOpenAiCompatibleConnectionProbe(
        implementation: OpenAiCompatibleConnectionProbe,
    ): SpeechConnectionProbe

    @Binds
    @IntoSet
    abstract fun bindDashScopeSynthesizer(
        implementation: DashScopeSynthesizer,
    ): SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindDashScopeOneShotRecognizer(
        implementation: DashScopeOneShotRecognizer,
    ): SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindDashScopeStreamingRecognizer(
        implementation: DashScopeStreamingRecognizer,
    ): StreamingSpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindMinimaxSynthesizer(
        implementation: MinimaxSynthesizer,
    ): SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindMinimaxRecognizer(
        implementation: MinimaxRecognizer,
    ): SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindMiMoSynthesizer(
        implementation: MiMoSynthesizer,
    ): SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindMiMoAudioRecognizer(
        implementation: MiMoAudioRecognizer,
    ): SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindOpenAiSpeechRecognizer(
        implementation: OpenAiSpeechRecognizer,
    ): SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindDeepSeekSpeechRecognizer(
        implementation: DeepSeekSpeechRecognizer,
    ): SpeechRecognizer

    @Binds
    @IntoSet
    abstract fun bindDeepSeekSpeechSynthesizer(
        implementation: DeepSeekSpeechSynthesizer,
    ): SpeechSynthesizer

    @Binds
    @IntoSet
    abstract fun bindOpenAiSpeechSynthesizer(
        implementation: OpenAiSpeechSynthesizer,
    ): SpeechSynthesizer

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
        ): StreamingSpeechSynthesizer = MinimaxBidiSynthesizer(speechWebSocketClient)

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
        ): SpeechSynthesizer = OpenAiCompatibleSynthesizer(protocol)

        @Provides
        @Singleton
        @IntoSet
        fun provideOpenAiCompatibleAudioRecognizer(
            protocol: OpenAiCompatibleChatProtocol,
        ): SpeechRecognizer = OpenAiCompatibleAudioRecognizer(protocol)

        @Provides
        @Singleton
        @IntoSet
        fun provideMinimaxVoiceProvider(
            okHttpClient: OkHttpClient,
        ): TtsVoiceProvider = MinimaxVoiceProvider(okHttpClient)
    }
}
