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
import github.ponyhuang.gimi.data.skills.EnabledSkillSource
import github.ponyhuang.gimi.data.skills.FileSkillRepository
import github.ponyhuang.gimi.data.skills.SkillArchiveReader
import github.ponyhuang.gimi.data.skills.SkillArchiveStore
import github.ponyhuang.gimi.data.skills.SkillEnabledStore
import github.ponyhuang.gimi.data.skills.SharedPreferencesSkillEnabledStore
import github.ponyhuang.gimi.data.skills.SkillsStorage
import github.ponyhuang.gimi.core.storage.ManagedDirectorySpec
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
    internal fun provideSnapshotRepository(
        implementation: github.ponyhuang.gimi.data.skills.FileSkillSnapshotRepository,
    ): github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotRepository = implementation

    @Provides
    @Singleton
    internal fun provideArchiveStore(paths: SkillStoragePaths): SkillArchiveStore =
        SkillArchiveStore(paths.skillsRoot, paths.stagingRoot)

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
    @Singleton
    internal fun provideSkillRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        paths: SkillStoragePaths,
        enabledStore: SkillEnabledStore,
        store: SkillArchiveStore,
    ): SkillRepository {
        val reader = SkillArchiveReader(
            okHttpClient = okHttpClient,
            localDocumentOpener = { value ->
                context.contentResolver.openInputStream(value.toUri())
            },
        )
        return FileSkillRepository(
            store = store,
            archiveReader = reader,
            enabledStore = enabledStore,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    @Provides
    @Singleton
    internal fun provideSkillEnabledStore(
        store: SharedPreferencesSkillEnabledStore,
    ): SkillEnabledStore = store

    @Provides
    @Singleton
    internal fun provideSkillSource(
        paths: SkillStoragePaths,
        enabledStore: SkillEnabledStore,
    ): SkillSource {
        paths.skillsRoot.mkdirs()
        // 运行时技能源包一层启用过滤：禁用的技能不进入 Agent 工具集，也不可按名加载。
        return EnabledSkillSource(
            delegate = NewFileSystemSource(paths.skillsRoot.absolutePath),
            disabledNames = enabledStore::disabledNames,
        )
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
