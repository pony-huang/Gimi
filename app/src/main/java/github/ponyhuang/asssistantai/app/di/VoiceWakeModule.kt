package github.ponyhuang.asssistantai.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.domain.speech.repository.VoiceWakeRepository
import github.ponyhuang.asssistantai.voice.BluetoothVoiceController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceWakeModule {
    @Binds
    @Singleton
    abstract fun bindVoiceWakeRepository(
        implementation: BluetoothVoiceController,
    ): VoiceWakeRepository
}
