package github.ponyhuang.gimi.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Type-safe destinations for the single-activity Compose navigation host. */
sealed interface AppRoute : NavKey {
    @Serializable
    data object Chat : AppRoute

    /**
     * Dedicated page for one structured local-file search response.
     *
     * @property sessionId Owning conversation used to prevent cross-session result display.
     * @property responseId Tool-response identifier used to restore the result from message state.
     */
    @Serializable
    data class ChatSearchResults(
        val sessionId: String,
        val responseId: String,
    ) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object RecommendationSettings : AppRoute

    @Serializable
    data object MemorySettings : AppRoute

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

    @Serializable
    data object PluginSettings : AppRoute

    /**
     * 单个插件的配置页。
     *
     * @property pluginId 目标插件的稳定唯一 id。
     */
    @Serializable
    data class PluginConfig(val pluginId: String) : AppRoute

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
