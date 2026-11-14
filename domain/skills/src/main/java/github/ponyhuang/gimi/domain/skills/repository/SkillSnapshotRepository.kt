package github.ponyhuang.gimi.domain.skills.repository

/** 导出已启用技能的完整资源快照；调用方必须在 finally 中释放临时归档。 */
interface SkillSnapshotRepository {
    suspend fun export(name: String): String
    suspend fun release(archivePath: String)
}

/** 准备技能失败的稳定原因，与环境启用状态区分。 */
class SkillSnapshotFailure(val reason: Reason) : IllegalStateException(reason.name) {
    enum class Reason { NotInstalled, Disabled }
}
