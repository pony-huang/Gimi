package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.BuildConfig

/** Settings landing page containing only available settings capabilities. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModelService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            item {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 28.dp),
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Tune,
                    title = "模型服务",
                    subtitle = "配置 API 密钥、地址和模型列表",
                    onClick = onNavigateToModelService,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp).weight(1f),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
