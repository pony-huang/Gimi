package github.ponyhuang.gimi.data.assistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.assistant.AssistantVoiceSessionStore
import github.ponyhuang.gimi.data.assistant.DefaultAssistantSessionCoordinator
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.domain.assistant.repository.VoiceSessionStore

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {

    @Binds
    abstract fun bindAssistantSessionCoordinator(
        impl: DefaultAssistantSessionCoordinator,
    ): AssistantSessionCoordinator

    @Binds
    abstract fun bindVoiceSessionStore(
        impl: AssistantVoiceSessionStore,
    ): VoiceSessionStore
}
