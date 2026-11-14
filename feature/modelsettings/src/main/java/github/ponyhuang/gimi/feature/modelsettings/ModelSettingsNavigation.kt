package github.ponyhuang.gimi.feature.modelsettings

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.feature.modelsettings.add.AddServiceRoute
import github.ponyhuang.gimi.feature.modelsettings.defaults.DefaultModelSettingsRoute
import github.ponyhuang.gimi.feature.modelsettings.detail.ServiceDetailRoute
import github.ponyhuang.gimi.feature.modelsettings.list.ModelServiceListRoute
import kotlinx.serialization.Serializable

/** Destinations owned by model settings. */
sealed interface ModelSettingsDestination : NavKey {
    /** 模型服务列表：已配置实例、添加入口与默认模型偏好。 */
    @Serializable
    data object ServiceList : ModelSettingsDestination

    /**
     * 添加服务目录：兼容接口与内置服务商模板。
     *
     * @property origin 从哪个默认模型用途选择器进入；保存后回到该选择器确认一次。
     */
    @Serializable
    data class AddService(val origin: DefaultModelRole? = null) : ModelSettingsDestination

    /**
     * 服务详情编辑器。
     *
     * @property instanceId 已有实例 ID；null 表示从 [templateId] 派生的新实例草稿。
     * @property templateId 新实例的来源模板 ID；自定义 LLM 服务为 null。
     * @property origin 从哪个默认模型用途选择器进入；保存后返回原选择器。
     */
    @Serializable
    data class ServiceDetail(
        val instanceId: String? = null,
        val templateId: String? = null,
        val origin: DefaultModelRole? = null,
    ) : ModelSettingsDestination

    /**
     * Default models for each application role.
     *
     * @property openRole 进入即打开该用途的选择器；用于从用途选择器引导补配服务或通话页引导。
     */
    @Serializable
    data class Defaults(val openRole: DefaultModelRole? = null) : ModelSettingsDestination
}

/** Resolves model-settings destinations and owns their internal transitions. */
@Composable
fun ModelSettingsEntryProvider(
    destination: NavKey,
    onBack: () -> Unit,
    navigate: (NavKey) -> Unit,
): Boolean = when (destination) {
    ModelSettingsDestination.ServiceList -> {
        ModelServiceListRoute(
            onBack = onBack,
            onNavigateToDetail = { instanceId ->
                navigate(ModelSettingsDestination.ServiceDetail(instanceId = instanceId))
            },
            onAddService = { navigate(ModelSettingsDestination.AddService()) },
            onOpenDefaults = { navigate(ModelSettingsDestination.Defaults()) },
        )
        true
    }

    is ModelSettingsDestination.AddService -> {
        AddServiceRoute(
            onBack = onBack,
            onOpenTemplate = { templateId ->
                navigate(ModelSettingsDestination.ServiceDetail(templateId = templateId, origin = destination.origin))
            },
            onOpenCustom = {
                navigate(ModelSettingsDestination.ServiceDetail(templateId = null, origin = destination.origin))
            },
        )
        true
    }

    is ModelSettingsDestination.ServiceDetail -> {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        ServiceDetailRoute(
            instanceId = destination.instanceId,
            templateId = destination.templateId,
            origin = destination.origin,
            onBack = onBack,
            onOpenUrl = { uriHandler.openUri(it) },
            // 保存新实例后弹栈回服务列表；再进默认模型页会叠加在列表之上。
            onOpenDefaults = { navigate(ModelSettingsDestination.Defaults(openRole = destination.origin)) },
        )
        true
    }

    is ModelSettingsDestination.Defaults -> {
        DefaultModelSettingsRoute(
            onBack = onBack,
            openRole = destination.openRole,
            onAddService = { role ->
                navigate(ModelSettingsDestination.AddService(origin = role))
            },
        )
        true
    }


    else -> false
}
