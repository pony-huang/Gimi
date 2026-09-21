package github.ponyhuang.gimi.data.speech.di

import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.data.speech.playback.AndroidSpeechPlaybackRepository
import github.ponyhuang.gimi.data.speech.remote.MiMoSpeechSynthesisGateway
import github.ponyhuang.gimi.data.speech.remote.MiMoVoiceProvider
import github.ponyhuang.gimi.data.speech.remote.MinimaxTtsGateway
import github.ponyhuang.gimi.data.speech.remote.MinimaxVoiceProvider
import github.ponyhuang.gimi.data.speech.remote.OpenAiCompatibleSpeechRecognitionGateway
import github.ponyhuang.gimi.data.speech.remote.SpeechRecognitionGateway
import github.ponyhuang.gimi.data.speech.repository.DefaultSpeechRecognitionRepository
import github.ponyhuang.gimi.data.speech.repository.DefaultSpeechSynthesisRepository
import github.ponyhuang.gimi.data.speech.repository.DefaultTtsVoiceRepository
import github.ponyhuang.gimi.data.speech.repository.SpeechSettingsPreferences
import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechSynthesisRepository
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceProvider
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceRepository
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechModule {

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
    abstract fun bindTtsVoiceRepository(
        implementation: DefaultTtsVoiceRepository,
    ): TtsVoiceRepository

    @Binds
    @IntoSet
    abstract fun bindMiMoVoiceProvider(
        implementation: MiMoVoiceProvider,
    ): TtsVoiceProvider

    companion object {
        @Provides
        @Singleton
        @IntoSet
        fun provideMinimaxVoiceProvider(
            okHttpClient: OkHttpClient,
        ): TtsVoiceProvider = MinimaxVoiceProvider(okHttpClient)
        @Provides
        @Singleton
        fun provideSpeechRecognitionGateway(
            okHttpClient: OkHttpClient,
        ): SpeechRecognitionGateway =
            OpenAiCompatibleSpeechRecognitionGateway(okHttpClient)

        @Provides
        @Singleton
        fun provideMinimaxSpeechSynthesisGateway(
            okHttpClient: OkHttpClient,
        ): MinimaxTtsGateway = MinimaxTtsGateway(okHttpClient, Gson())

        @Provides
        @Singleton
        fun provideMiMoSpeechSynthesisGateway(
            okHttpClient: OkHttpClient,
        ): MiMoSpeechSynthesisGateway = MiMoSpeechSynthesisGateway(okHttpClient, Gson())
    }
}