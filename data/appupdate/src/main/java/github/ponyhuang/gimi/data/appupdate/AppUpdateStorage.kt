package github.ponyhuang.gimi.data.appupdate

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

/** appupdate capability 的受管目录声明。 */
object AppUpdateStorage {
    const val PACKAGES_ID = "appupdate.packages"

    val Packages = ManagedDirectorySpec(
        id = PACKAGES_ID,
        owner = "appupdate",
        area = StorageArea.CACHE,
        relativePath = "shareable/appupdate/packages",
        lifecycle = StorageLifecycle.TEMPORARY,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.FILE_PROVIDER,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateStorageModule {
    @Provides
    @IntoSet
    fun providePackagesDirectorySpec(): ManagedDirectorySpec = AppUpdateStorage.Packages

    @Provides
    @IntoSet
    fun provideStorageMaintenanceHandler(): StorageMaintenanceHandler =
        FileTreeStorageMaintenanceHandler("appupdate")
}
