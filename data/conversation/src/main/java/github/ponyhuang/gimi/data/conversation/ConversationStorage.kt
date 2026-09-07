package github.ponyhuang.gimi.data.conversation

import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle
import github.ponyhuang.gimi.domain.conversation.model.ConversationStorageIds

/** Persistent archive for attachment payloads that belong to saved chat turns. */
val conversationAttachmentDirectorySpec = ManagedDirectorySpec(
    id = ConversationStorageIds.ATTACHMENTS,
    owner = "data:conversation",
    area = StorageArea.FILES,
    relativePath = "conversation/attachments",
    lifecycle = StorageLifecycle.PERSISTENT,
    backupPolicy = BackupPolicy.INCLUDED,
    sharingPolicy = SharingPolicy.PRIVATE,
)

/** Temporary attachment drafts that may be discarded by storage maintenance. */
val conversationDraftDirectorySpec = ManagedDirectorySpec(
    id = ConversationStorageIds.REPOSITORY_DRAFTS,
    owner = "data:conversation",
    area = StorageArea.CACHE,
    relativePath = "conversation/drafts",
    lifecycle = StorageLifecycle.TEMPORARY,
    backupPolicy = BackupPolicy.EXCLUDED,
    sharingPolicy = SharingPolicy.PRIVATE,
)
