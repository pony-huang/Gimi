package github.ponyhuang.asssistantai.di

import android.content.Context
import android.util.Log
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.artifacts.FileArtifactService
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.agent.AgentFactory
import github.ponyhuang.asssistantai.agent.AgentLLMModelFactory
import github.ponyhuang.asssistantai.agent.LocalToolCatalog
import github.ponyhuang.asssistantai.agent.conversation.AdkChatAgentRepository
import github.ponyhuang.asssistantai.agent.plugins.ConversationGenerateTitlePlugin
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.LocalToolDefinitionSource
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.asssistantai.domain.mcp.repository.McpRepository
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
    fun providePlugins(
        agentLLMModelFactory: AgentLLMModelFactory
    ): List<@JvmSuppressWildcards Plugin> {
        return listOf(
            ConversationGenerateTitlePlugin(agentLLMModelFactory)
        )
    }

    @Provides
    @Singleton
    fun provideAgentChatRunner(
        sessionService: SessionService,
        artifactService: ArtifactService,
        agentFactory: AgentFactory,
        modelServices: AgentModelConfigurationSource,
        toolAuthorization: ToolAuthorizationRepository,
        mcpRepository: McpRepository,
        plugins: List<@JvmSuppressWildcards Plugin>,
    ): AgentChatRunner = AgentChatRunner(
        factory = { selection, allowConfirmationRequiredTools, toolConfiguration ->
            modelServices.awaitReady()
            agentFactory.create(
                selection = selection,
                allowConfirmationRequiredTools = allowConfirmationRequiredTools,
                toolConfiguration = toolConfiguration,
            )
        },
        sessionService = sessionService,
        artifactService = artifactService,
        configurationRevision = {
            Triple(
                toolAuthorization.revision.value,
                mcpRepository.revision.value,
                modelServices.configurationRevision.value,
            )
        },
        plugins = plugins,
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
