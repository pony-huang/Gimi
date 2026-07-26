package github.ponyhuang.asssistantai.data.speech.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.data.speech.playback.AndroidSpeechPlaybackRepository
import github.ponyhuang.asssistantai.data.speech.remote.MiMoSpeechSynthesisGateway
import github.ponyhuang.asssistantai.data.speech.remote.OpenAiCompatibleSpeechRecognitionGateway
import github.ponyhuang.asssistantai.data.speech.remote.SpeechRecognitionGateway
import github.ponyhuang.asssistantai.data.speech.remote.SpeechSynthesisGateway
import github.ponyhuang.asssistantai.data.speech.repository.DefaultSpeechRecognitionRepository
import github.ponyhuang.asssistantai.data.speech.repository.DefaultSpeechSynthesisRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechRecognitionRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechSynthesisRepository
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
        fun provideSpeechSynthesisGateway(
            okHttpClient: OkHttpClient,
        ): SpeechSynthesisGateway = MiMoSpeechSynthesisGateway(okHttpClient)
    }
}
