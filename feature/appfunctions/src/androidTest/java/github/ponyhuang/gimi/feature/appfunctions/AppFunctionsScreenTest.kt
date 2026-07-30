package github.ponyhuang.gimi.feature.appfunctions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionSelection
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Rule
import org.junit.Test

class AppFunctionsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unsupportedDeviceShowsVersionNoticeAndDisablesMasterSwitch() {
        composeRule.setContent {
            AsssistantaiTheme {
                AppFunctionsSettingsScreen(
                    state = AppFunctionsUiState(
                        catalog = AppFunctionCatalogState(
                            support = AppFunctionsSupport.UNSUPPORTED_DEVICE,
                        ),
                    ),
                    onAction = {},
                    onOpenApp = {},
                )
            }
        }

        composeRule.onNodeWithText("AppFunctions 尝鲜功能").assertIsDisplayed()
        composeRule.onNodeWithText("当前设备不支持").assertIsDisplayed()
        composeRule.onNodeWithTag("appfunctions-master-switch").assertIsNotEnabled()
    }

    @Test
    fun enabledCatalogShowsApplicationCounts() {
        val function = descriptor("create_note")
        val state = AppFunctionsUiState(
            catalog = AppFunctionCatalogState(
                support = AppFunctionsSupport.AVAILABLE,
                selection = AppFunctionSelection(
                    featureEnabled = true,
                    enabledPackageNames = setOf(function.key.packageName),
                    enabledFunctionKeys = setOf(function.key),
                ),
                functions = listOf(function),
            ),
        )
        composeRule.setContent {
            AsssistantaiTheme {
                AppFunctionsSettingsScreen(
                    state = state,
                    onAction = {},
                    onOpenApp = {},
                )
            }
        }

        composeRule.onNodeWithText("Notes").assertIsDisplayed()
        composeRule.onNodeWithText("已启用 1 / 1；不支持 0").assertIsDisplayed()
    }

    @Test
    fun functionDetailShowsSearchFiltersAndFunctionSwitches() {
        val function = descriptor("create_note")
        val state = AppFunctionsUiState(
            catalog = AppFunctionCatalogState(
                support = AppFunctionsSupport.AVAILABLE,
                selection = AppFunctionSelection(
                    featureEnabled = true,
                    enabledPackageNames = setOf(function.key.packageName),
                    enabledFunctionKeys = setOf(function.key),
                ),
                functions = listOf(function),
            ),
        )
        composeRule.setContent {
            AsssistantaiTheme {
                AppFunctionAppDetailScreen(
                    packageName = function.key.packageName,
                    state = state,
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithText("搜索函数").assertIsDisplayed()
        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("create_note").assertIsDisplayed()
    }

    private fun descriptor(functionId: String) = AppFunctionDescriptor(
        key = AppFunctionKey("com.example.notes", functionId),
        appLabel = "Notes",
        appDescription = "Notes provider",
        description = "Create a note",
        parameters = emptyList(),
        providerEnabled = true,
        supported = true,
    )
}
