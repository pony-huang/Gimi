package github.ponyhuang.gimi.feature.modelsettings

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.feature.modelsettings.defaults.DefaultModelSettingsRoute
import github.ponyhuang.gimi.feature.modelsettings.detail.LLMModelSettingDetailRoute
import github.ponyhuang.gimi.feature.modelsettings.list.ModelServiceListRoute
import kotlinx.serialization.Serializable

/** Destinations owned by model settings. */
sealed interface ModelSettingsDestination : NavKey {
    /** Configured model-provider list. */
    @Serializable
    data object ServiceList : ModelSettingsDestination

    /** Default models for each application role. */
    @Serializable
    data object Defaults : ModelSettingsDestination

    /**
     * Configuration for one model provider.
     *
     * @property serviceId Stable provider identifier.
     */
    @Serializable
    data class ServiceDetail(val serviceId: String) : ModelSettingsDestination
}

/** Resolves model-settings destinations and owns their internal list-to-detail transition. */
@Composable
fun ModelSettingsEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
): Boolean = when (destination) {
    ModelSettingsDestination.ServiceList -> {
        ModelServiceListRoute(
            onBack = onBack,
            onNavigateToDetail = { navigate(ModelSettingsDestination.ServiceDetail(it)) },
        )
        true
    }

    ModelSettingsDestination.Defaults -> {
        DefaultModelSettingsRoute(onBack = onBack)
        true
    }

    is ModelSettingsDestination.ServiceDetail -> {
        LLMModelSettingDetailRoute(
            serviceId = destination.serviceId,
            onBack = onBack,
        )
        true
    }

    else -> false
}
