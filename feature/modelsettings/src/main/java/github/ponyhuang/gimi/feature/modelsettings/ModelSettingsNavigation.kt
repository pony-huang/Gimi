package github.ponyhuang.gimi.feature.modelsettings

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind
import github.ponyhuang.gimi.feature.modelsettings.defaults.DefaultModelSettingsRoute
import github.ponyhuang.gimi.feature.modelsettings.detail.LLMModelSettingDetailRoute
import github.ponyhuang.gimi.feature.modelsettings.list.ModelServiceListRoute
import kotlinx.serialization.Serializable

/** Destinations owned by model settings. */
sealed interface ModelSettingsDestination : NavKey {
    /** Configured model-provider list. */
    @Serializable
    data object ServiceList : ModelSettingsDestination

    /** 可添加的厂商目录。 */
    @Serializable
    data object ServiceProviders : ModelSettingsDestination

    /** 已添加的语音厂商列表。 */
    @Serializable
    data object SpeechServices : ModelSettingsDestination

    /** 可添加的语音厂商目录。 */
    @Serializable
    data object SpeechProviders : ModelSettingsDestination

    /**
     * Default models for each application role.
     *
     * @property openRealtimePicker 进入即打开 Realtime 模型选择；用于从通话页引导配置。
     */
    @Serializable
    data class Defaults(val openRealtimePicker: Boolean = false) : ModelSettingsDestination

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
            onAddService = { navigate(ModelSettingsDestination.ServiceProviders) },
        )
        true
    }

    ModelSettingsDestination.ServiceProviders -> {
        ModelServiceListRoute(
            onBack = onBack,
            onNavigateToDetail = { navigate(ModelSettingsDestination.ServiceDetail(it)) },
            onAddService = {},
            providerPicker = true,
        )
        true
    }

    ModelSettingsDestination.SpeechServices -> {
        ModelServiceListRoute(
            onBack = onBack,
            onNavigateToDetail = { navigate(ModelSettingsDestination.ServiceDetail(it)) },
            onAddService = { navigate(ModelSettingsDestination.SpeechProviders) },
            serviceKind = ModelServiceKind.Speech,
        )
        true
    }

    ModelSettingsDestination.SpeechProviders -> {
        ModelServiceListRoute(
            onBack = onBack,
            onNavigateToDetail = { navigate(ModelSettingsDestination.ServiceDetail(it)) },
            onAddService = {},
            providerPicker = true,
            serviceKind = ModelServiceKind.Speech,
        )
        true
    }

    is ModelSettingsDestination.Defaults -> {
        DefaultModelSettingsRoute(
            onBack = onBack,
            openRealtimePicker = destination.openRealtimePicker,
        )
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
