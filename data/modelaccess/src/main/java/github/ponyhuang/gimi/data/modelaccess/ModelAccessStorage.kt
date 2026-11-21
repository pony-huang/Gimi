package github.ponyhuang.gimi.data.modelaccess

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import github.ponyhuang.gimi.core.storage.BackupPolicy
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.SharingPolicy
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageLifecycle

/** modelaccess capability 的受管目录声明。 */
object ModelAccessStorage {
    const val VENDOR_FILES_ID = "modelaccess.vendorFiles"

    /** 厂商 Files API 的本地上传暂存目录；仅本进程读写，不对外共享。 */
    val VendorFiles = ManagedDirectorySpec(
        id = VENDOR_FILES_ID,
        owner = "modelaccess",
        area = StorageArea.CACHE,
        relativePath = "vendor-files",
        lifecycle = StorageLifecycle.CACHE,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object ModelAccessStorageModule {
    @Provides
    @IntoSet
    fun provideVendorFilesDirectorySpec(): ManagedDirectorySpec = ModelAccessStorage.VendorFiles
}
