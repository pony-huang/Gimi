package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.environment.EnvironmentFileBridge
import github.ponyhuang.gimi.domain.environment.EnvironmentRepository
import github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotFailure
import github.ponyhuang.gimi.domain.skills.repository.SkillSnapshotRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 技能准备与文件交换的 Agent 入口，复用环境工具集的发现与授权链路。 */
class EnvironmentBridgeTools @Inject constructor(
    private val bridge: EnvironmentFileBridge,
    private val snapshots: SkillSnapshotRepository,
    private val environment: EnvironmentRepository,
) {
    fun tools(): List<BaseTool> = listOf(
        tool("prepare_skill", "Prepare an enabled skill's complete directory for local execution. Returns skill_path. Inspect dependency files and use execute/start_job to install missing dependencies. Keep the returned directory unchanged.", listOf("name")),
        tool("import_file_to_environment", "Copy an authorized content URI from search_documents/search_media_files or an App workspace file path into this conversation's inputs. Returns Linux path. name is a filename including extension.", listOf("source", "name")),
        tool("publish_environment_file", "Publish one completed file from this conversation's Linux workspace to the App shared workspace. Returns the App file path for opening and sharing.", listOf("path")),
    )

    private fun tool(toolName: String, text: String, fields: List<String>): BaseTool = object : FunctionTool(
        name = toolName,
        description = text,
        requiresConfirmation = toolName == "import_file_to_environment",
    ) {
        override fun declaration() = FunctionDeclaration(
            name = name, description = description,
            parameters = Schema(type = Type.OBJECT, properties = fields.associateWith { Schema(type = Type.STRING) }, required = fields),
        )

        override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any {
            if (!environment.status().let { it.enabled && it.runtimeUsable }) return mapOf("error_code" to "EnvironmentUnavailable", "error" to "Use get_environment_status to diagnose local runtime availability.")
            val key = context.session.key
            val session = "${key.userId.length}:${key.userId}${requireNotNull(key.id)}"
            fun argument(name: String) = (args[name] as? String)?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing $name")
            return try {
                when (toolName) {
                    "prepare_skill" -> {
                        val archive = snapshots.export(argument("name"))
                        val path = try { bridge.prepareSkill(session, archive) }
                            finally { withContext(NonCancellable) { snapshots.release(archive) } }
                        mapOf("skill_path" to path, "dependencies" to "Inspect requirements.txt/package.json and check installed interpreters before executing.")
                    }
                    "import_file_to_environment" -> mapOf("path" to bridge.importFile(session, argument("source"), argument("name")))
                    else -> mapOf("app_path" to bridge.publishFile(session, argument("path")))
                }
            } catch (error: SkillSnapshotFailure) {
                val hint = if (error.reason == SkillSnapshotFailure.Reason.NotInstalled)
                    "Install the downloaded skill into the App library with install_skill before prepare_skill."
                else "Enable this skill in App skill settings."
                mapOf("error_code" to "Skill${error.reason.name}", "error" to hint)
            } catch (error: IOException) {
                mapOf("error" to "File exchange failed. Check destination before retrying publication.")
            } catch (error: IllegalArgumentException) {
                mapOf("error" to "Invalid or unauthorized file/skill reference, or transfer exceeds limits.")
            } catch (error: IllegalStateException) {
                mapOf("error_code" to "EnvironmentUnavailable", "error" to "Use get_environment_status to diagnose local runtime availability.")
            } catch (error: SecurityException) {
                mapOf("error" to "File access denied.")
            }
        }
    }
}
