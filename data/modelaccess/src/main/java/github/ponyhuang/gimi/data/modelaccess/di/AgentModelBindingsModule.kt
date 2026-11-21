package github.ponyhuang.gimi.data.modelaccess.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.modelaccess.DefaultAgentLLMModelFactory
import github.ponyhuang.gimi.data.modelaccess.openai.realtime.OpenAiRealtimeConnectionProbe
import github.ponyhuang.gimi.data.modelaccess.qwen.realtime.QwenRealtimeConnectionProbe
import github.ponyhuang.gimi.data.modelaccess.shared.OpenAiRealtimeVoiceSupport
import github.ponyhuang.gimi.data.modelaccess.shared.QwenRealtimeVoiceSupport
import github.ponyhuang.gimi.domain.modelaccess.AgentLlmModelSource
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceSupport
import github.ponyhuang.gimi.domain.voice.repository.SpeechConnectionProbe

/** 绑定随实现走：厂商探测/音色支持与模型选择的具体类在此绑定到 domain 契约。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModelBindingsModule {
    @Binds
    abstract fun bindAgentLlmModelSource(
        implementation: DefaultAgentLLMModelFactory,
    ): AgentLlmModelSource

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindOpenAiRealtimeConnectionProbe(
        implementation: OpenAiRealtimeConnectionProbe,
    ): SpeechConnectionProbe

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindQwenRealtimeConnectionProbe(
        implementation: QwenRealtimeConnectionProbe,
    ): SpeechConnectionProbe

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindOpenAiRealtimeVoiceSupport(
        implementation: OpenAiRealtimeVoiceSupport,
    ): RealtimeVoiceSupport

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindQwenRealtimeVoiceSupport(
        implementation: QwenRealtimeVoiceSupport,
    ): RealtimeVoiceSupport
}
