package github.ponyhuang.gimi.domain.environment

/** App 与本地环境之间的文件交换，返回值为执行路径或已发布的 App 文件路径。 */
interface EnvironmentFileBridge {
    suspend fun importFile(session: String, source: String, name: String): String
    suspend fun publishFile(session: String, path: String): String
    suspend fun prepareSkill(session: String, archivePath: String): String
}
