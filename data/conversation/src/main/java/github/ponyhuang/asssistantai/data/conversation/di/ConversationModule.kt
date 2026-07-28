package github.ponyhuang.asssistantai.data.conversation.di

import android.content.Context
import androidx.room.Room
import com.google.adk.kt.sessions.SessionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.data.conversation.ChatDisplayPreferences
import github.ponyhuang.asssistantai.data.conversation.attachment.AndroidChatAttachmentRepository
import github.ponyhuang.asssistantai.data.conversation.repository.AdkConversationRepository
import github.ponyhuang.asssistantai.data.conversation.runtime.InMemoryAgentRuntimeGate
import github.ponyhuang.asssistantai.data.conversation.local.ConversationMetadataDatabase
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.asssistantai.domain.conversation.repository.ConversationRepository
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentSessionIdentity
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConversationModule {

    @Provides
    @Singleton
    fun provideAgentRuntimeGate(
        implementation: InMemoryAgentRuntimeGate,
    ): AgentRuntimeGate = implementation

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
        ConversationMetadataDatabase.DATABASE_NAME,
    ).fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    @Singleton
    fun provideConversationRepository(
        @ApplicationContext context: Context,
        sessionService: SessionService,
        database: ConversationMetadataDatabase,
    ): ConversationRepository = AdkConversationRepository(
        appName = AgentSessionIdentity.APP_NAME,
        userId = AgentSessionIdentity.DEFAULT_USER_ID,
        sessionService = sessionService,
        metadataDao = database.conversationMetadataDao(),
        context = context,
    )
}
