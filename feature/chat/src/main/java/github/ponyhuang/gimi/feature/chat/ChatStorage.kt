package github.ponyhuang.gimi.feature.chat

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.FileTreeStorageMaintenanceHandler
import github.ponyhuang.gimi.core.storage.StorageMaintenanceHandler
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.domain.conversation.model.ConversationStorageIds

internal val chatComposerDraftDirectorySpec = ManagedDirectorySpec(
    id = ConversationStorageIds.COMPOSER_DRAFTS,
    owner = "feature:chat",
    area = StorageArea.CACHE,
    relativePath = "conversation/composer-drafts",
    lifecycle = StorageLifecycle.TEMPORARY,
    backupPolicy = BackupPolicy.EXCLUDED,
    sharingPolicy = SharingPolicy.PRIVATE,
)

internal val chatShareableDirectorySpec = ManagedDirectorySpec(
    id = ConversationStorageIds.SHAREABLE,
    owner = "feature:chat",
    area = StorageArea.CACHE,
    relativePath = "shareable/chat",
    lifecycle = StorageLifecycle.TEMPORARY,
    backupPolicy = BackupPolicy.EXCLUDED,
    sharingPolicy = SharingPolicy.FILE_PROVIDER,
)

@Module
@InstallIn(SingletonComponent::class)
internal object ChatStorageModule {
    @Provides
    @IntoSet
    fun provideComposerDraftDirectorySpec(): ManagedDirectorySpec = chatComposerDraftDirectorySpec

    @Provides
    @IntoSet
    fun provideShareableDirectorySpec(): ManagedDirectorySpec = chatShareableDirectorySpec

    @Provides
    @IntoSet
    fun provideStorageMaintenanceHandler(): StorageMaintenanceHandler =
        FileTreeStorageMaintenanceHandler("feature:chat")
}
