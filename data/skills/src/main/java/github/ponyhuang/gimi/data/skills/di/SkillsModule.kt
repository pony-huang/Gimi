package github.ponyhuang.gimi.data.skills.di

import android.content.Context
import android.net.Uri
import com.google.adk.kt.skills.NewFileSystemSource
import com.google.adk.kt.skills.SkillSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.skills.FileSkillRepository
import github.ponyhuang.gimi.data.skills.SkillArchiveReader
import github.ponyhuang.gimi.data.skills.SkillArchiveStore
import github.ponyhuang.gimi.data.skills.SkillsStorage
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
import github.ponyhuang.gimi.core.storage.FileTreeStorageMaintenanceHandler
import github.ponyhuang.gimi.core.storage.StorageMaintenanceHandler
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import java.io.File
import javax.inject.Singleton
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import androidx.core.net.toUri

@Module
@InstallIn(SingletonComponent::class)
object SkillsModule {
    @Provides
    @Singleton
    internal fun providePaths(
        registry: StorageRegistry,
    ): SkillStoragePaths = SkillStoragePaths(
        skillsRoot = registry.resolve(SkillsStorage.INSTALLED_ID),
        stagingRoot = registry.resolve(SkillsStorage.IMPORTS_ID),
    )

    @Provides
    @IntoSet
    internal fun provideInstalledDirectorySpec(): ManagedDirectorySpec = SkillsStorage.Installed

    @Provides
    @IntoSet
    internal fun provideImportsDirectorySpec(): ManagedDirectorySpec = SkillsStorage.Imports

    @Provides
    @IntoSet
    internal fun provideStorageMaintenanceHandler(): StorageMaintenanceHandler =
        FileTreeStorageMaintenanceHandler("skills")

    @Provides
    @Singleton
    internal fun provideSkillRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        paths: SkillStoragePaths,
    ): SkillRepository {
        val reader = SkillArchiveReader(
            okHttpClient = okHttpClient,
            localDocumentOpener = { value ->
                context.contentResolver.openInputStream(value.toUri())
            },
        )
        return FileSkillRepository(
            store = SkillArchiveStore(paths.skillsRoot, paths.stagingRoot),
            archiveReader = reader,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    @Provides
    @Singleton
    internal fun provideSkillSource(paths: SkillStoragePaths): SkillSource {
        paths.skillsRoot.mkdirs()
        return NewFileSystemSource(paths.skillsRoot.absolutePath)
    }
}

/**
 * 技能仓储使用的已安装目录与导入暂存目录。
 *
 * @property skillsRoot 已安装技能的持久目录。
 * @property stagingRoot 导入和解包过程使用的临时目录。
 */
internal data class SkillStoragePaths(
    val skillsRoot: File,
    val stagingRoot: File,
)
