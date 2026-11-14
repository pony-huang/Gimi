package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.environment.EnvironmentRepository
import java.io.IOException
import javax.inject.Inject

/** 始终可查询的只读状态入口，环境工具被门控时仍提供准确恢复指引。 */
class EnvironmentStatusTool @Inject constructor(
    private val repository: EnvironmentRepository,
) : BaseTool(
    name = "get_environment_status",
    description = "Check local Linux runtime availability, installation, enablement and version compatibility. " +
        "Always available. Use when execute/prepare_skill are missing or fail. " +
        "Skill installation via install_skill works independently of this environment. " +
        "Never infer that the skill library is unavailable from a disabled runtime.",
) {
    override fun declaration() = FunctionDeclaration(name = name, description = description,
        parameters = Schema(type = Type.OBJECT, properties = emptyMap()))

    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any = try {
        val status = repository.status()
        val state = when {
            !status.available -> "runtime_missing"
            !status.connectionAvailable -> "connection_unavailable"
            status.updateRequired -> "update_required"
            status.installing -> "installing"
            status.failed -> "installation_failed"
            !status.installed -> "environment_not_installed"
            !status.enabled -> "disabled"
            else -> "ready"
        }
        val hint = when (state) {
            "ready" -> "Use execute/start_job. Install App skills with install_skill, then load_skill and prepare_skill."
            "runtime_missing" -> "Open App environment settings and install the companion runtime."
            "connection_unavailable" -> "Open App environment settings and reconnect the runtime."
            "update_required" -> "Open App environment settings and update the companion runtime to the bundled version."
            "installing" -> "Environment installation is in progress. Check again after it completes."
            "installation_failed" -> "Open App environment settings and inspect the installation failure."
            "environment_not_installed" -> "Open App environment settings and install the Linux environment."
            else -> "Open App environment settings and enable/reconnect the local runtime. Component process restarts reset enablement."
        }
        mapOf("state" to state, "execution_available" to (state == "ready"),
            "skill_installation_available" to true, "next_step" to hint,
            "background_restricted" to status.backgroundRestricted)
    } catch (_: IOException) {
        mapOf("state" to "connection_unavailable", "execution_available" to false,
            "next_step" to "Open App environment settings and reconnect the runtime. install_skill remains available.")
    } catch (_: SecurityException) {
        mapOf("state" to "permission_denied", "execution_available" to false,
            "next_step" to "Check that App and companion runtime have matching signatures. install_skill remains available.")
    }
}
