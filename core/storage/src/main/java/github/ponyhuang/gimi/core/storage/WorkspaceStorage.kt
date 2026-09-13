package github.ponyhuang.gimi.core.storage

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * 附件工作区的逻辑目录 id。
 *
 * 工作区由两个 capability 共同消费：`:data:conversation` 把发送的附件归档进工作区，
 * `:data:workspace` 提供文件管理（列表/删除/占用）。目录声明放在 core 是因为 id 常量
 * 与 [ManagedDirectorySpec] 必须对双方可见，跨能力互相依赖属于捷径依赖。
 */
object WorkspaceStorageIds {
    /** 全局共享附件工作区：跨会话公用、用户自管生命周期。 */
    const val WORKSPACE = "workspace.files"
}

/**
 * 全局共享附件工作区的受管目录声明。
 *
 * 平铺目录（无子目录），归档文件名内嵌显示名便于用户辨认。PERSISTENT 表示永不自动
 * 删除——工作区文件没有应用内清理入口，只能由用户在工作区管理界面显式删除。
 * owner 标记为 workspace 能力，见 `openspec/specs/attachment-workspace/spec.md`。
 */
val workspaceDirectorySpec = ManagedDirectorySpec(
    id = WorkspaceStorageIds.WORKSPACE,
    owner = "workspace",
    area = StorageArea.FILES,
    relativePath = "workspace",
    lifecycle = StorageLifecycle.PERSISTENT,
    backupPolicy = BackupPolicy.EXCLUDED,
    sharingPolicy = SharingPolicy.PRIVATE,
)

@Module
@InstallIn(SingletonComponent::class)
internal object WorkspaceStorageModule {
    @Provides
    @IntoSet
    fun provideWorkspaceDirectorySpec(): ManagedDirectorySpec = workspaceDirectorySpec
}
