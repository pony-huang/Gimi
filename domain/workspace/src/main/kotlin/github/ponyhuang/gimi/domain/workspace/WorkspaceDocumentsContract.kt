package github.ponyhuang.gimi.domain.workspace

/**
 * 共享缓存区（附件工作区）向系统文件管理器暴露文档树的契约常量。
 *
 * 主应用通过 Android 官方 `DocumentsProvider` 暴露 `files/workspace/` 目录，设置页根据
 * 本契约拼装 `DocumentsContract` Root URI 并拉起系统文件管理界面。
 */
object WorkspaceDocumentsContract {
    /** DocumentsProvider authority 后缀；运行时前缀为宿主包名（兼容 debug/release 共存）。 */
    const val AUTHORITY_SUFFIX = ".workspace.documents"

    /** 缓存区在 DocumentsProvider 中的唯一 Root ID。 */
    const val ROOT_ID = "workspace"

    /** 缓存区根目录对应的 Document ID 前缀。 */
    const val ROOT_DOCUMENT_ID = "root:"

    /** 按应用包名生成缓存区 DocumentsProvider 的完整 authority。 */
    fun authority(packageName: String): String = "$packageName$AUTHORITY_SUFFIX"
}
