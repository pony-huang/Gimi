package github.ponyhuang.gimi.data.environment

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

/** environment capability 的受管目录声明。 */
object EnvironmentStorage {
    const val COMPANION_APK_ID = "environment.companionApk"

    /** 内置配套运行组件 APK；位于 FileProvider 的 `shareable/` 路径下，供系统安装器读取。 */
    val CompanionApk = ManagedDirectorySpec(
        id = COMPANION_APK_ID,
        owner = "environment",
        area = StorageArea.CACHE,
        relativePath = "shareable/environment/packages",
        lifecycle = StorageLifecycle.TEMPORARY,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.FILE_PROVIDER,
    )
}

@Module
@InstallIn(SingletonComponent::class)
object EnvironmentStorageModule {
    @Provides
    @IntoSet
    fun provideCompanionApkDirectorySpec(): ManagedDirectorySpec = EnvironmentStorage.CompanionApk
}
