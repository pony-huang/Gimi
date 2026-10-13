package github.ponyhuang.gimi.feature.mcp

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun McpServerAddOptionsScreen(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
        ) {
            PreferenceSectionTitle(text = stringResource(R.string.mcp_section_add_methods))
            PreferenceGroupCard {
                PreferenceListItem(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.mcp_method_new_title),
                    subtitle = stringResource(R.string.mcp_method_new_subtitle),
                    onClick = onCreate,
                    showDivider = true,
                )
                PreferenceListItem(
                    icon = Icons.Default.ContentPaste,
                    title = stringResource(R.string.mcp_method_import_title),
                    subtitle = stringResource(R.string.mcp_method_import_subtitle),
                    onClick = onImport,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun McpServerAddOptionsScreenPreview() {
    AsssistantaiTheme {
        McpServerAddOptionsScreen(
            onCreate = {},
            onImport = {},
        )
    }
}
