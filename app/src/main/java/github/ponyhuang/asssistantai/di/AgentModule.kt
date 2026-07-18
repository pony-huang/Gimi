package github.ponyhuang.asssistantai.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.artifacts.FileArtifactService
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.agent.AgentFactory
import github.ponyhuang.asssistantai.data.ConversationRepository
import github.ponyhuang.asssistantai.data.ConversationMetadataDatabase
import github.ponyhuang.asssistantai.data.ModelServiceDatabase
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.speech.OpenAiCompatibleSpeechRecognitionClient
import github.ponyhuang.asssistantai.speech.SpeechRecognitionClient
import java.io.File
import javax.inject.Singleton

/**
 * Agent 运行所需的进程级单例 — SessionService / ArtifactService / AgentChatRunner。
 *
 * 设计要点：
 * - 三个绑定都标 `@Singleton`，与原先 `AsssistantaiApp` 字段的"进程级单例"语义一致。
 * - 文件 artifact 根目录沿用原先 `pickArtifactsRoot` 的策略：优先 `<externalFilesDir>/adk/artifacts`，
 *   外置存储不可用时退到 `<filesDir>/adk/artifacts`。
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {

    private const val TAG: String = "AgentModule"

    @Provides
    @Singleton
    fun provideSpeechRecognitionClient(): SpeechRecognitionClient =
        OpenAiCompatibleSpeechRecognitionClient()

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
        agentFactory: AgentFactory,
        modelServices: ModelServiceRepository,
    ): AgentChatRunner = AgentChatRunner(
        factory = {
            modelServices.awaitReady()
            agentFactory.create()
        },
        sessionService = sessionService,
        artifactService = artifactService,
    )

    /**
     * ADK SessionService 之上的 UI 友好仓库 — 提供 [Conversation] 列表与会话 CRUD。
     *
     * 进程级单例 — [ChatViewModel] 与后续潜在的其他 ViewModel 共享同一份会话缓存。
     */
    @Provides
    @Singleton
    fun provideConversationRepository(
        sessionService: SessionService,
        metadataDatabase: ConversationMetadataDatabase,
    ): ConversationRepository = ConversationRepository(
        appName = AgentChatRunner.APP_NAME,
        userId = USER_ID,
        sessionService = sessionService,
        metadataDao = metadataDatabase.conversationMetadataDao(),
    )

    @Provides
    @Singleton
    fun provideConversationMetadataDatabase(
        @ApplicationContext context: Context,
    ): ConversationMetadataDatabase = Room.databaseBuilder(
        context,
        ConversationMetadataDatabase::class.java,
        "conversation-metadata.db",
    ).build()

    @Provides
    @Singleton
    fun provideModelServiceDatabase(
        @ApplicationContext context: Context,
    ): ModelServiceDatabase = Room.databaseBuilder(
        context,
        ModelServiceDatabase::class.java,
        "model-services.db",
    ).build()

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

    /**
     * 进程级稳定的 userId — 派生自进程启动时刻，让 Room 里的所有会话都归属于同一 user。
     */
    private const val USER_ID: String = "user-default"
}
