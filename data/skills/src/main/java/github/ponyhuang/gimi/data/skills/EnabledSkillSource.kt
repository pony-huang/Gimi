package github.ponyhuang.gimi.data.skills

import com.google.adk.kt.skills.Frontmatter
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.skills.SkillSourceException

/**
 * 技能源过滤装饰器：把被禁用的技能从运行时视角隐藏。
 *
 * 列表查询直接过滤结果；按名加载时以 [SkillSourceException] 失败返回，
 * 消息面向 LLM，须自包含且可指导模型调整行为。禁用状态每次调用即时读取，
 * 与设置页的开关一致，不缓存旧快照。
 */
internal class EnabledSkillSource(
    private val delegate: SkillSource,
    private val disabledNames: () -> Set<String>,
) : SkillSource {

    override suspend fun listFrontmatters(): Result<List<Frontmatter>> {
        val disabled = disabledNames()
        return delegate.listFrontmatters().mapCatching { frontmatters ->
            frontmatters.filterNot { it.name in disabled }
        }
    }

    override suspend fun listResources(
        skillName: String,
        resourceDirectoryPath: String,
    ): Result<List<String>> = guardDisabled(skillName) { delegate.listResources(skillName, resourceDirectoryPath) }

    override suspend fun loadFrontmatter(skillName: String): Result<Frontmatter> =
        guardDisabled(skillName) { delegate.loadFrontmatter(skillName) }

    override suspend fun loadInstructions(skillName: String): Result<String> =
        guardDisabled(skillName) { delegate.loadInstructions(skillName) }

    override suspend fun loadResource(skillName: String, resourcePath: String): Result<ByteArray> =
        guardDisabled(skillName) { delegate.loadResource(skillName, resourcePath) }

    private suspend fun <T> guardDisabled(
        skillName: String,
        block: suspend () -> Result<T>,
    ): Result<T> {
        if (skillName in disabledNames()) return disabledFailure(skillName)
        return block()
    }

    private fun disabledFailure(skillName: String): Result<Nothing> = Result.failure(
        SkillSourceException(
            "The skill \"$skillName\" is disabled by the user. " +
                "Do not retry it; continue without this skill or suggest the user enable it in settings.",
        ),
    )
}
