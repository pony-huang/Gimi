package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.sessions.SessionService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.domain.conversation.repository.SubtaskRunRepository
import javax.inject.Singleton

/** 子任务能力在拥有者数据模块内绑定；不引入 feature 或 app 依赖。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubtaskModule {
    @Binds
    abstract fun repository(store: SubtaskSessionStore): SubtaskRunRepository

    @Binds
    @IntoSet
    abstract fun contribution(contribution: DelegateTasksContribution): AgentContribution

    companion object {
        @Provides
        @Singleton
        fun executor(
            sessions: SessionService,
            store: SubtaskSessionStore,
            tools: SubtaskReadOnlyToolset,
            models: AgentLLMModelFactory,
        ): SubtaskBatchExecutor = SubtaskBatchExecutor(sessions, store, tools, models::createModel)
    }
}
