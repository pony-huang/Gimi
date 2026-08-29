package github.ponyhuang.gimi.di

import android.content.Context
import android.util.Log
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.artifacts.FileArtifactService
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.agent.AdkMcpConnectionTester
import github.ponyhuang.gimi.agent.AdkMcpSkipReporter
import github.ponyhuang.gimi.agent.AgentChatRunner
import github.ponyhuang.gimi.agent.AgentBuildConfigurationSnapshot
import github.ponyhuang.gimi.agent.AgentFactory
import github.ponyhuang.gimi.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.agent.LocalToolCatalog
import github.ponyhuang.gimi.agent.conversation.AdkChatAgentRepository
import github.ponyhuang.gimi.agent.plugins.ConversationGenerateTitlePlugin
import github.ponyhuang.gimi.agent.plugins.MemoryPersistencePlugin
import github.ponyhuang.gimi.agent.tools.search.MiniLmToolEmbeddingModel
import github.ponyhuang.gimi.agent.tools.search.MyObjectBox
import github.ponyhuang.gimi.agent.tools.search.ObjectBoxToolVectorSearch
import github.ponyhuang.gimi.agent.tools.search.ToolEmbeddingModel
import github.ponyhuang.gimi.agent.tools.search.ToolVectorEntity
import github.ponyhuang.gimi.agent.tools.search.ToolVectorSearch
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.mcp.repository.McpConnectionTester
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.mcp.repository.McpSkipReporter
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.domain.toolauthorization.repository.LocalToolDefinitionSource
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File
import javax.inject.Singleton

/**
 * Agent 运行所需的进程级单例。
 *
 * 提供的绑定：
 * - [SessionService] — 基于 Room 的会话持久化（[RoomSessionService]）
 * - [ArtifactService] — 基于文件的 artifact 存储（[FileArtifactService]）
 * - [LocalToolDefinitionSource] — 本地工具定义（委托给 [LocalToolCatalog]）
 * - [List]<[Plugin]> — 业务插件（[ConversationGenerateTitlePlugin] 等）
 * - [AgentChatRunner] — 聊天运行器，组合上述所有服务
 *
 * 文件 artifact 根目录：优先 `<externalFilesDir>/adk/artifacts`，
 * 外置存储不可用时退到 `<filesDir>/adk/artifacts`。
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    private const val TAG: String = "AgentModule"

    @Provides
    @Singleton
    fun provideLocalToolDefinitionSource(
        catalog: LocalToolCatalog,
    ): LocalToolDefinitionSource = catalog

    @Provides
    @Singleton
    fun provideMcpConnectionTester(
        implementation: AdkMcpConnectionTester,
    ): McpConnectionTester = implementation

    @Provides
    @Singleton
    fun provideMcpSkipReporter(
        implementation: AdkMcpSkipReporter,
    ): McpSkipReporter = implementation

    @Provides
    @Singleton
    fun provideToolEmbeddingModel(
        implementation: MiniLmToolEmbeddingModel,
    ): ToolEmbeddingModel = implementation

    @Provides
    @Singleton
    fun provideToolVectorSearch(
        implementation: ObjectBoxToolVectorSearch,
    ): ToolVectorSearch = implementation

    @Provides
    @Singleton
    fun provideToolVectorBoxStore(
        @ApplicationContext context: Context,
    ): BoxStore = MyObjectBox.builder()
        .androidContext(context)
        .name("tool-vector-search")
        .build()

    @Provides
    @Singleton
    fun provideToolVectorBox(
        store: BoxStore,
    ): Box<ToolVectorEntity> = store.boxFor(ToolVectorEntity::class.java)

    @Provides
    @Singleton
    fun provideChatAgentRepository(
        implementation: AdkChatAgentRepository,
    ): ChatAgentRepository = implementation

    @Provides
    @Singleton
    fun provideSessionService(
        @ApplicationContext context: Context,
    ): SessionService = RoomSessionService.fromContext(context)

    @Provides
    @Singleton
    fun provideArtifactService(
        @ApplicationContext context: Context,
    ): ArtifactService {
        val root = pickArtifactsRoot(context)
        Log.i(TAG, "FileArtifactService root: $root")
        return FileArtifactService(root)
    }

    @Provides
    @Singleton
    fun provideAgentChatRunner(
        sessionService: SessionService,
        artifactService: ArtifactService,
        memoryService: MemoryService,
        agentFactory: AgentFactory,
        modelServices: AgentModelConfigurationSource,
        toolAuthorization: ToolAuthorizationRepository,
        mcpRepository: McpRepository,
        agentLLMModelFactory: AgentLLMModelFactory,
        pluginRuntimeProvider: PluginRuntimeProvider<AgentPlugin>,
    ): AgentChatRunner = AgentChatRunner(
        factory = { selection, toolAccessMode, pluginRuntime ->
            modelServices.awaitReady()
            agentFactory.create(
                selection = selection,
                toolAccessMode = toolAccessMode,
                pluginRuntime = pluginRuntime,
            )
        },
        sessionService = sessionService,
        artifactService = artifactService,
        memoryService = memoryService,
        configuration = {
            val pluginRuntime = pluginRuntimeProvider.runtime.value
            AgentBuildConfigurationSnapshot(
                revision = listOf(
                    toolAuthorization.revision.value,
                    mcpRepository.revision.value,
                    modelServices.configurationRevision.value,
                    pluginRuntime.revision,
                ),
                pluginRuntime = pluginRuntime,
            )
        },
        plugins = { pluginRuntime ->
            buildList<Plugin> {
                add(ConversationGenerateTitlePlugin(agentLLMModelFactory))
                add(MemoryPersistencePlugin())
                addAll(pluginRuntime.enabledPlugins)
            }
        },
    )

    /**
     * 选 artifact 根目录：优先外置存储，失败时退到内部 files dir。
     */
    private fun pickArtifactsRoot(context: Context): String {
        val externalDir = context.getExternalFilesDir(null)
        return if (externalDir != null) {
            File(externalDir, FileArtifactService.DEFAULT_ARTIFACTS_SUBDIR).path
        } else {
            Log.w(TAG, "External files dir unavailable; using internal files dir for artifacts.")
            File(context.filesDir, FileArtifactService.DEFAULT_ARTIFACTS_SUBDIR).path
        }
    }
}
