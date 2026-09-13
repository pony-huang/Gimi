package github.ponyhuang.gimi.feature.workspace

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

/**
 * 预览副本目录（`cache/shareable/workspace/previews/`）：打开工作区文件前先拷贝到这个
 * FileProvider 可共享的 cache 目录，外部查看器只接触副本，不直接消费工作区原件。
 * TEMPORARY 生命周期允许存储维护随时回收。
 */
internal val workspacePreviewDirectorySpec = ManagedDirectorySpec(
    id = WorkspacePreviewStorageIds.PREVIEWS,
    owner = "feature:workspace",
    area = StorageArea.CACHE,
    relativePath = "shareable/workspace",
    lifecycle = StorageLifecycle.TEMPORARY,
    backupPolicy = BackupPolicy.EXCLUDED,
    sharingPolicy = SharingPolicy.FILE_PROVIDER,
)

/** 预览副本目录的逻辑 id（与工作区本体 `workspace.files` 分开声明）。 */
object WorkspacePreviewStorageIds {
    const val PREVIEWS = "workspace.previews"
}

@Module
@InstallIn(SingletonComponent::class)
internal object WorkspaceStorageModule {
    @Provides
    @IntoSet
    fun providePreviewDirectorySpec(): ManagedDirectorySpec = workspacePreviewDirectorySpec
}
