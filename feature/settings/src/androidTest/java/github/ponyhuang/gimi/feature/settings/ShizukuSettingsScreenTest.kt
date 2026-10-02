package github.ponyhuang.gimi.feature.settings

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShizukuSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledEntryOnlyOffersTheSwitch() {
        val actions = mutableListOf<SettingsAction>()
        show(SettingsUiState(), actions)
        composeRule.onNodeWithText("Shizuku").assertHasNoClickAction()
        composeRule.onNodeWithContentDescription("Shizuku").performClick()
        assertEquals(listOf(SettingsAction.SetMobileUseEnabled(true)), actions)
    }

    @Test
    fun enabledSwitchAndEntryHaveSeparateActions() {
        val actions = mutableListOf<SettingsAction>()
        show(SettingsUiState(mobileUseEnabled = true), actions)
        composeRule.onNodeWithContentDescription("Shizuku").performClick()
        assertEquals(listOf(SettingsAction.SetMobileUseEnabled(false)), actions)
        composeRule.onNodeWithText("Shizuku").performClick()
        assertEquals(listOf(SettingsAction.SetMobileUseEnabled(false), SettingsAction.OpenMobileUse), actions)
    }

    @Test
    fun switchAndNavigationAreUnavailableDuringShutdown() {
        show(SettingsUiState(mobileUseEnabled = true, mobileUseUpdating = true), mutableListOf())
        composeRule.onNodeWithContentDescription("Shizuku").assertIsNotEnabled()
        composeRule.onNodeWithText("Shizuku").assertHasNoClickAction()
    }

    private fun show(state: SettingsUiState, actions: MutableList<SettingsAction>) {
        composeRule.setContent {
            AsssistantaiTheme { SettingsScreen(state, "1.0", actions::add) }
        }
    }
}
