package github.ponyhuang.asssistantai.data.conversation.di

import android.content.Context
import androidx.room.Room
import com.google.adk.kt.sessions.SessionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.agent.AgentChatRunner
import github.ponyhuang.asssistantai.core.database.destructiveForPrototype
import github.ponyhuang.asssistantai.data.conversation.local.ConversationMetadataDatabase
import github.ponyhuang.asssistantai.data.ChatDisplayPreferences
import github.ponyhuang.asssistantai.data.conversation.attachment.AndroidChatAttachmentRepository
import github.ponyhuang.asssistantai.data.conversation.repository.AdkChatAgentRepository
import github.ponyhuang.asssistantai.data.conversation.repository.AdkConversationRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ConversationRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConversationModule {

    @Provides
    @Singleton
    fun provideChatAgentRepository(
        implementation: AdkChatAgentRepository,
    ): ChatAgentRepository = implementation

    @Provides
    @Singleton
    fun provideChatAttachmentRepository(
        implementation: AndroidChatAttachmentRepository,
    ): ChatAttachmentRepository = implementation

    @Provides
    @Singleton
    fun provideChatDisplayRepository(
        implementation: ChatDisplayPreferences,
    ): ChatDisplayRepository = implementation

    @Provides
    @Singleton
    fun provideConversationMetadataDatabase(
        @ApplicationContext context: Context,
    ): ConversationMetadataDatabase = Room.databaseBuilder(
        context,
        ConversationMetadataDatabase::class.java,
        "conversation-metadata.db",
    ).destructiveForPrototype().build()

    @Provides
    @Singleton
    fun provideConversationRepository(
        sessionService: SessionService,
        database: ConversationMetadataDatabase,
    ): ConversationRepository = AdkConversationRepository(
        appName = AgentChatRunner.APP_NAME,
        userId = USER_ID,
        sessionService = sessionService,
        metadataDao = database.conversationMetadataDao(),
    )

    private const val USER_ID = "user-default"
}
