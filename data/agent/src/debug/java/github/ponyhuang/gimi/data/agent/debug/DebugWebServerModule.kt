package github.ponyhuang.gimi.data.agent.debug

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 仅 debug 变体编译：把 [AgentDebugWebServer] 绑定到真实 ADK webserver 实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DebugWebServerModule {

    @Binds
    @Singleton
    abstract fun bindAgentDebugWebServer(impl: AdkAgentDebugWebServer): AgentDebugWebServer
}
