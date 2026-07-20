package github.ponyhuang.asssistantai.feature.permissions

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.asssistantai.domain.permissions.model.AppPermission
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingDeniedGroupRaisesRequestAction() {
        var action: PermissionSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                PermissionSettingsScreen(
                    state = PermissionSettingsUiState(
                        groups = listOf(
                            PermissionGroupUiModel(
                                kind = PermissionGroupKind.Microphone,
                                titleRes = R.string.permission_name_microphone,
                                subtitleRes = R.string.permission_desc_microphone,
                                permissions = listOf(AppPermission.RecordAudio),
                                status = PermissionGroupStatus.Denied,
                            ),
                        ),
                    ),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText("麦克风").performClick()

        assertEquals(
            PermissionSettingsAction.RequestGroup(PermissionGroupKind.Microphone),
            action,
        )
    }
}
