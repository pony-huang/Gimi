package github.ponyhuang.gimi.data.speech.di

import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.speech.playback.AndroidSpeechPlaybackRepository
import github.ponyhuang.gimi.data.speech.remote.MiMoSpeechSynthesisGateway
import github.ponyhuang.gimi.data.speech.remote.MinimaxTtsGateway
import github.ponyhuang.gimi.data.speech.remote.OpenAiCompatibleSpeechRecognitionGateway
import github.ponyhuang.gimi.data.speech.remote.SpeechRecognitionGateway
import github.ponyhuang.gimi.data.speech.repository.DefaultSpeechRecognitionRepository
import github.ponyhuang.gimi.data.speech.repository.DefaultSpeechSynthesisRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.speech.repository.SpeechSynthesisRepository
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

    companion object {
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