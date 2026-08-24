package github.ponyhuang.gimi.data.voicewake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceController
import github.ponyhuang.gimi.data.voicewake.notification.AndroidWakeModelNotifier
import github.ponyhuang.gimi.data.voicewake.notification.WakeModelNotifier
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceWakeModule {
    @Binds
    @Singleton
    abstract fun bindWakeModelNotifier(
        implementation: AndroidWakeModelNotifier,
    ): WakeModelNotifier

    @Binds
    @Singleton
    abstract fun bindVoiceWakeRepository(
        implementation: BluetoothVoiceController,
    ): VoiceWakeRepository
}
