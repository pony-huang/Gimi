package github.ponyhuang.gimi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.runner.AndroidJUnit4
import github.ponyhuang.gimi.DebugTestHostActivity
import github.ponyhuang.gimi.feature.appfunction.AppFunctionDestination
import github.ponyhuang.gimi.feature.a2a.RemoteAgentDestination
import github.ponyhuang.gimi.feature.chat.ChatDestination
import github.ponyhuang.gimi.feature.environment.EnvironmentDestination
import github.ponyhuang.gimi.feature.mcp.McpDestination
import github.ponyhuang.gimi.feature.memory.MemoryDestination
import github.ponyhuang.gimi.feature.mobileuse.MobileUseDestination
import github.ponyhuang.gimi.feature.modelsettings.ModelSettingsDestination
import github.ponyhuang.gimi.feature.permissions.PermissionDestination
import github.ponyhuang.gimi.feature.plugin.PluginDestination
import github.ponyhuang.gimi.feature.recommendation.RecommendationDestination
import github.ponyhuang.gimi.feature.settings.SettingsDestination
import github.ponyhuang.gimi.feature.sandbox.SandboxDestination
import github.ponyhuang.gimi.feature.skills.SkillsDestination
import github.ponyhuang.gimi.feature.workspace.WorkspaceDestination
import github.ponyhuang.gimi.ui.theme.GimiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 导航冒烟回归：把全部类型安全 destination 逐个压入导航栈，验证每屏都能完成组合
 * 与 ViewModel 创建（空数据/占位参数下允许空态，不允许崩溃）。未注册的 key 会被
 * [AppEntryContent] 的 fail-fast check 捕获；MainActivity 的权限弹窗与后台服务
 * 副作用不在此测试范围内，故不直接拉起它。
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationSmokeTest {
    @get:Rule(order = 0)
    val composeRule = createAndroidComposeRule<DebugTestHostActivity>()

    /** 需要必填参数的目的地使用占位值；对应页面应展示空态/错误态而非崩溃。 */
    private val destinations: List<NavKey> = listOf(
        ChatDestination.SimpleChatPromptSettings,
        SettingsDestination.Settings,
        SettingsDestination.About,
        SettingsDestination.PrivacyPolicy,
        ModelSettingsDestination.ServiceList,
        ModelSettingsDestination.AddService(),
        ModelSettingsDestination.ServiceDetail(),
        ModelSettingsDestination.Defaults(),
        EnvironmentDestination.Overview,
        EnvironmentDestination.Local,
        EnvironmentDestination.Variables,
        SandboxDestination.Settings,
        McpDestination.ServerList,
        McpDestination.Tools("smoke-server"),
        McpDestination.AddOptions,
        McpDestination.Import,
        McpDestination.Editor(),
        RemoteAgentDestination.AgentList,
        RemoteAgentDestination.Editor(),
        AppFunctionDestination.Apps,
        AppFunctionDestination.Info("smoke.package"),
        PluginDestination.Settings,
        PluginDestination.Configuration("smoke-plugin"),
        PluginDestination.Subscription,
        PluginDestination.Detail("smoke-entry"),
        MobileUseDestination.Settings,
        RecommendationDestination.Settings,
        RecommendationDestination.UpdateSettings,
        MemoryDestination.Settings,
        MemoryDestination.History,
        PermissionDestination.Settings,
        SkillsDestination.Settings,
        WorkspaceDestination.Manager,
    )

    @Test
    fun everyDestinationComposesWithoutCrashing() {
        // NavBackStack 支持独立构造：逐个 push 并等重组稳定，组合崩溃会直接抛给测试。
        val backStack = NavBackStack<NavKey>(ChatDestination.Chat)
        composeRule.setContent {
            GimiTheme {
                AppSmokeNavHost(backStack)
            }
        }
        destinations.forEach { key ->
            composeRule.runOnUiThread { backStack.add(key) }
            composeRule.waitForIdle()
            assertEquals(key, backStack.last())
        }
        assertEquals(destinations.size + 1, backStack.size)
    }

    @Composable
    private fun AppSmokeNavHost(backStack: NavBackStack<NavKey>) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeAt(backStack.lastIndex) },
            // 与 MainScreen 一致：把 hiltViewModel() 绑定到单个 NavEntry。
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { destination ->
                NavEntry(
                    key = destination,
                    contentKey = destination.navigationContentKey(),
                ) {
                    AppEntryContent(
                        destination = destination,
                        goBack = { backStack.removeAt(backStack.lastIndex) },
                        navigate = { backStack.add(it) },
                        replaceCurrent = { key ->
                            backStack.removeAt(backStack.lastIndex)
                            backStack.add(key)
                        },
                        returnToChat = {},
                        chatLaunchRequest = null,
                        sharedMediaUris = emptyList(),
                        onSharedMediaConsumed = {},
                    )
                }
            },
        )
    }
}
