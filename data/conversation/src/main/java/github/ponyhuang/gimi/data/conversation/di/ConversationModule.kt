package github.ponyhuang.gimi.data.conversation.di

import android.content.Context
import androidx.room.Room
import com.google.adk.kt.sessions.SessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.Provides
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.data.conversation.attachment.AndroidChatAttachmentRepository
import github.ponyhuang.gimi.data.conversation.conversationAttachmentDirectorySpec
import github.ponyhuang.gimi.data.conversation.conversationDraftDirectorySpec
import github.ponyhuang.gimi.data.conversation.conversationExportDirectorySpec
import github.ponyhuang.gimi.data.conversation.export.AndroidConversationExportRepository
import github.ponyhuang.gimi.data.conversation.local.ConversationMetadataDatabase
import github.ponyhuang.gimi.data.conversation.repository.AdkConversationRepository
import github.ponyhuang.gimi.data.conversation.repository.DefaultConversationSessionResolver
import github.ponyhuang.gimi.data.conversation.runtime.InMemoryAgentRuntimeGate
import github.ponyhuang.gimi.data.conversation.ToolAccessPreferences
import github.ponyhuang.gimi.data.conversation.ToolApprovalPreferences
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRepository
import github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ToolAccessRepository
import github.ponyhuang.gimi.domain.conversation.repository.ToolApprovalRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConversationModule {

    @Provides
    @Singleton
    fun provideConversationExportRepository(
        implementation: AndroidConversationExportRepository,
    ): ConversationExportRepository = implementation

    @Provides
    @IntoSet
    fun provideConversationAttachmentDirectorySpec(): ManagedDirectorySpec =
        conversationAttachmentDirectorySpec

    @Provides
    @IntoSet
    fun provideConversationDraftDirectorySpec(): ManagedDirectorySpec =
        conversationDraftDirectorySpec

    @Provides
    @IntoSet
    fun provideConversationExportDirectorySpec(): ManagedDirectorySpec =
        conversationExportDirectorySpec

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
    fun provideToolApprovalRepository(
        implementation: ToolApprovalPreferences,
    ): ToolApprovalRepository = implementation

    @Provides
    @Singleton
    fun provideToolAccessRepository(
        implementation: ToolAccessPreferences,
    ): ToolAccessRepository = implementation

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
        subtasks: github.ponyhuang.gimi.domain.conversation.repository.SubtaskRunRepository,
    ): ConversationRepository = AdkConversationRepository(
        appName = AgentSessionIdentity.APP_NAME,
        userId = AgentSessionIdentity.DEFAULT_USER_ID,
        sessionService = sessionService,
        metadataDao = database.conversationMetadataDao(),
        context = context,
        subtasks = subtasks,
    )

    @Provides
    @Singleton
    fun provideConversationSessionResolver(
        implementation: DefaultConversationSessionResolver,
    ): ConversationSessionResolver = implementation

}
