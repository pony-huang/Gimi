package github.ponyhuang.gimi.data.voicewake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.voicewake.SystemVoiceWakeController
import github.ponyhuang.gimi.data.voicewake.AndroidOnDeviceVoiceWakeRecognizer
import github.ponyhuang.gimi.data.voicewake.VoiceWakeRecognizer
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceWakeModule {
    @Binds
    @Singleton
    abstract fun bindVoiceWakeRecognizer(
        implementation: AndroidOnDeviceVoiceWakeRecognizer,
    ): VoiceWakeRecognizer

    @Binds
    @Singleton
    abstract fun bindVoiceWakeRepository(
        implementation: SystemVoiceWakeController,
    ): VoiceWakeRepository
}
