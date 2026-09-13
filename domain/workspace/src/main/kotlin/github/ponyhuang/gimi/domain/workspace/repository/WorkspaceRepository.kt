package github.ponyhuang.gimi.domain.workspace.repository

import github.ponyhuang.gimi.domain.workspace.model.WorkspaceFile

/**
 * 共享工作区的文件管理契约。
 *
 * 工作区文件永不自动删除：删除会话等任何会话级操作都不触碰工作区，唯一的删除入口是
 * 用户通过本契约显式执行。实现应保证 [delete] 只作用于工作区目录内的文件。
 */
interface WorkspaceRepository {

    /**
     * 列出工作区中的全部归档文件，按最后修改时间倒序。
     *
     * 瞬时写入（如归档过程的临时文件）不应出现在结果中。
     */
    suspend fun list(): List<WorkspaceFile>

    /** 工作区当前总占用的字节数。 */
    suspend fun totalBytes(): Long

    /**
     * 删除 [file] 对应的工作区文件。
     *
     * @return 删除成功返回 true；文件已不存在、路径越权（不在工作区根目录内）或删除
     *   失败返回 false，调用方据此逐项上报，不应中断批量删除的其余项。
     */
    suspend fun delete(file: WorkspaceFile): Boolean
}
