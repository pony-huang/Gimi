package github.ponyhuang.gimi.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Type-safe destinations for the single-activity Compose navigation host. */
sealed interface AppRoute : NavKey {
    @Serializable
    data object Chat : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object DefaultModelSettings : AppRoute

    @Serializable
    data object VoiceWakeSettings : AppRoute

    @Serializable
    data object WorkFilesSettings : AppRoute

    @Serializable
    data object PermissionSettings : AppRoute

    @Serializable
    data object ToolAuthorizationSettings : AppRoute

    @Serializable
    data object ToolAuthorizationConfiguration : AppRoute

    /** AppFunctions 尝鲜总开关及提供应用目录。 */
    @Serializable
    data object AppFunctionsSettings : AppRoute

    /**
     * 单个提供应用的函数授权页。
     *
     * @property packageName 提供应用包名。
     */
    @Serializable
    data class AppFunctionAppDetail(val packageName: String) : AppRoute

    @Serializable
    data object SkillsSettings : AppRoute

    @Serializable
    data object ModelServiceList : AppRoute

    @Serializable
    data class ModelServiceDetail(val serviceId: String) : AppRoute

    @Serializable
    data object McpServerList : AppRoute

    @Serializable
    data class McpServerEditor(val serverId: String? = null) : AppRoute

    @Serializable
    data object McpServerImport : AppRoute

    @Serializable
    data object McpServerAddOptions : AppRoute
}
