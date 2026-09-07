package github.ponyhuang.gimi.domain.workfiles.model

/**
 * 已授权工作目录中的一个文件搜索结果。
 *
 * @property contentUri 可用于读取文件的 content URI。
 * @property displayName provider 返回的文件名。
 * @property mimeType 文件 MIME 类型。
 * @property sizeBytes 文件大小；provider 未知时为零。
 * @property modifiedTimeMillis 最后修改时间；provider 未知时为零。
 */
data class WorkFileSearchResult(
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTimeMillis: Long,
)
