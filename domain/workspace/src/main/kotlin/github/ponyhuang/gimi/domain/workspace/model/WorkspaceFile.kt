package github.ponyhuang.gimi.domain.workspace.model

/** 工作区文件的大类，由 [WorkspaceFile.type] 推导，用于选择列表图标与预览方式。 */
enum class WorkspaceFileType {
    IMAGE,
    AUDIO,
    DOCUMENT,
    OTHER,
}

/**
 * 共享工作区中的一个归档文件。
 *
 * 工作区是跨会话公用的用户资产目录：文件系统是唯一真源，[WorkspaceFile] 只是某个时刻
 * 的只读快照，列表刷新后内容可能已被用户或其他会话改变。
 *
 * @property name 文件名（含扩展名），内嵌归档时的显示名。
 * @property path 绝对路径；发送链路与预览打开都按路径消费。
 * @property sizeBytes 文件字节数。
 * @property lastModifiedMillis 最后修改时间（epoch 毫秒），列表默认按它倒序。
 * @property mimeType 由扩展名推断的 MIME 类型；无法推断时为 null。
 */
data class WorkspaceFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String? = null,
) {
    /** 按 MIME 前缀推导的大类；无法识别归为 [WorkspaceFileType.OTHER]。 */
    val type: WorkspaceFileType
        get() = when {
            mimeType?.startsWith("image/") == true -> WorkspaceFileType.IMAGE
            mimeType?.startsWith("audio/") == true -> WorkspaceFileType.AUDIO
            mimeType?.startsWith("text/") == true -> WorkspaceFileType.DOCUMENT
            mimeType?.startsWith("application/") == true -> WorkspaceFileType.DOCUMENT
            else -> WorkspaceFileType.OTHER
        }
}
