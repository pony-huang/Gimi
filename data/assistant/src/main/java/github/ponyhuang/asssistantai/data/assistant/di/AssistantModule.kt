package github.ponyhuang.asssistantai.data.assistant.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.data.assistant.AssistantVoiceSessionStore
import github.ponyhuang.asssistantai.data.assistant.DefaultAssistantSessionCoordinator
import github.ponyhuang.asssistantai.data.assistant.DefaultAssistantSystemStatusRepository
import github.ponyhuang.asssistantai.data.assistant.VoiceSessionIdStore
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSystemStatusRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {

    @Binds
    abstract fun bindAssistantSessionCoordinator(
        impl: DefaultAssistantSessionCoordinator,
    ): AssistantSessionCoordinator

    @Binds
    abstract fun bindVoiceSessionIdStore(
        impl: AssistantVoiceSessionStore,
    ): VoiceSessionIdStore

    @Binds
    abstract fun bindAssistantSystemStatusRepository(
        impl: DefaultAssistantSystemStatusRepository,
    ): AssistantSystemStatusRepository
}
