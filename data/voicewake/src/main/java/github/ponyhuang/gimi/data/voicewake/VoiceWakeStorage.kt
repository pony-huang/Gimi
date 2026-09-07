package github.ponyhuang.gimi.data.voicewake

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

/** voicewake capability 的受管目录声明。 */
object VoiceWakeStorage {
    const val MODELS_ID = "voicewake.models"
    const val DOWNLOADS_ID = "voicewake.downloads"

    val Models = ManagedDirectorySpec(
        id = MODELS_ID,
        owner = "voicewake",
        area = StorageArea.FILES,
        relativePath = "voicewake/models",
        lifecycle = StorageLifecycle.PERSISTENT,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
    val Downloads = ManagedDirectorySpec(
        id = DOWNLOADS_ID,
        owner = "voicewake",
        area = StorageArea.CACHE,
        relativePath = "voicewake/downloads",
        lifecycle = StorageLifecycle.TEMPORARY,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object VoiceWakeStorageModule {
    @Provides
    @IntoSet
    fun provideModelsDirectorySpec(): ManagedDirectorySpec = VoiceWakeStorage.Models

    @Provides
    @IntoSet
    fun provideDownloadsDirectorySpec(): ManagedDirectorySpec = VoiceWakeStorage.Downloads

    @Provides
    @IntoSet
    fun provideStorageMaintenanceHandler(): StorageMaintenanceHandler =
        FileTreeStorageMaintenanceHandler("voicewake")
}
