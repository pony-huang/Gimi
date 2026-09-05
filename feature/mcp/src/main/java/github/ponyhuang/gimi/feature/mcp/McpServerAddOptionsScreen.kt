package github.ponyhuang.gimi.feature.mcp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceNavigationCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun McpServerAddOptionsScreen(
    onCreate: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
        ) {
            PreferenceSectionTitle(text = stringResource(R.string.mcp_section_add_methods))
            PreferenceGroupCard {
                PreferenceNavigationCard(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.mcp_method_new_title),
                    subtitle = stringResource(R.string.mcp_method_new_subtitle),
                    onClick = onCreate,
                    showDivider = true,
                )
                PreferenceNavigationCard(
                    icon = Icons.Default.ContentPaste,
                    title = stringResource(R.string.mcp_method_import_title),
                    subtitle = stringResource(R.string.mcp_method_import_subtitle),
                    onClick = onImport,
                )
            }
        }
    }
}
