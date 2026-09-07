package github.ponyhuang.gimi.data.plugin

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
import java.io.File

/** plugin capability 的受管目录声明。 */
object PluginStorage {
    const val OPTIMIZED_ROOT_ID = "plugin.optimized-root"

    val OptimizedRoot = ManagedDirectorySpec(
        id = OPTIMIZED_ROOT_ID,
        owner = "plugin",
        area = StorageArea.CODE_CACHE,
        relativePath = "plugin/optimized",
        lifecycle = StorageLifecycle.CACHE,
        backupPolicy = BackupPolicy.EXCLUDED,
        sharingPolicy = SharingPolicy.PRIVATE,
    )
}

/** 在插件优化根目录下解析一个不可越界的包名子目录。 */
internal fun pluginOptimizedDirectory(root: File, packageName: String): File {
    require(packageName.matches(Regex("[A-Za-z0-9_.]+"))) { "Invalid plugin package name" }
    val canonicalRoot = root.canonicalFile
    val directory = File(canonicalRoot, packageName).canonicalFile
    require(directory.parentFile == canonicalRoot) { "Plugin optimized directory escapes its root" }
    return directory
}

@Module
@InstallIn(SingletonComponent::class)
object PluginStorageModule {
    @Provides
    @IntoSet
    fun provideOptimizedRootDirectorySpec(): ManagedDirectorySpec = PluginStorage.OptimizedRoot

    @Provides
    @IntoSet
    fun provideStorageMaintenanceHandler(): StorageMaintenanceHandler =
        FileTreeStorageMaintenanceHandler("plugin")
}
