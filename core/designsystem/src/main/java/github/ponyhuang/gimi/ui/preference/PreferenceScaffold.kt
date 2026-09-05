package github.ponyhuang.gimi.ui.preference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** Shared settings-page shell that preserves the existing app-bar and back behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    // 与页面画布同色，让应用栏融入浅灰/深色画布。
                    containerColor = preferenceCanvasColor(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = { actions() },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceScaffoldPreview() {
    AsssistantaiTheme {
        PreferenceScaffold(
            title = "设置页标题",
            onBack = {},
        ) { modifier ->
            PreferencePageContainer(modifier = modifier) {
                PreferenceSectionTitle(text = "分组标题")
            }
        }
    }
}
