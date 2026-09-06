package github.ponyhuang.gimi.data.agent.debug

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * release 变体的空实现 — 不携带任何 webserver 代码，[start]/[stop] 均为空操作。
 */
@Singleton
class DisabledAgentDebugWebServer @Inject constructor() : AgentDebugWebServer {
    override fun start() = Unit
    override fun stop() = Unit
}

/** 仅 release 变体编译：把 [AgentDebugWebServer] 绑定到空实现。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReleaseWebServerModule {

    @Binds
    @Singleton
    abstract fun bindAgentDebugWebServer(impl: DisabledAgentDebugWebServer): AgentDebugWebServer
}
