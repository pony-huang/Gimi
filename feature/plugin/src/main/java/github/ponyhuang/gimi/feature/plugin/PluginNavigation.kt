package github.ponyhuang.gimi.feature.plugin

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by plugin settings. */
sealed interface PluginDestination : NavKey {
    /** Installed plugin list and subscription catalog. */
    @Serializable
    data object Settings : PluginDestination

    /**
     * Configuration UI for one plugin.
     *
     * @property pluginId Stable plugin identifier.
     */
    @Serializable
    data class Configuration(val pluginId: String) : PluginDestination

    /** 插件订阅源地址编辑页。 */
    @Serializable
    data object Subscription : PluginDestination

    /**
     * 插件详细信息页。
     *
     * @property entryKey 目录条目 key，同时对应插件运行时 `pluginId`。
     */
    @Serializable
    data class Detail(val entryKey: String) : PluginDestination
}

/** Resolves plugin destinations and owns the list-to-configuration transition. */
@Composable
fun PluginEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
): Boolean = when (destination) {
    PluginDestination.Settings -> {
        PluginSettingsRoute(
            onBack = onBack,
            onOpenSubscription = { navigate(PluginDestination.Subscription) },
            onNavigateToConfig = { navigate(PluginDestination.Configuration(it)) },
            onNavigateToDetail = { navigate(PluginDestination.Detail(it)) },
        )
        true
    }

    is PluginDestination.Configuration -> {
        PluginConfigRoute(
            pluginId = destination.pluginId,
            onBack = onBack,
        )
        true
    }

    PluginDestination.Subscription -> {
        PluginSubscriptionRoute(onBack = onBack)
        true
    }

    is PluginDestination.Detail -> {
        PluginDetailRoute(
            entryKey = destination.entryKey,
            onBack = onBack,
            onNavigateToConfig = { navigate(PluginDestination.Configuration(it)) },
        )
        true
    }

    else -> false
}
