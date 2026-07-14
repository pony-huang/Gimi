package github.ponyhuang.asssistantai.ui.model.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.data.ModelProvider
import github.ponyhuang.asssistantai.ui.model.ModelServiceIcon

/**
 * 详情页头部：服务图标 + 大标题 + 总开关 + 外链 IconButton。
 */
@Composable
fun HeaderSection(
    service: ModelProvider,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModelServiceIcon(
            serviceId = service.serviceId,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = service.serviceName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Switch(
            checked = service.isEnabled,
            onCheckedChange = onToggleEnabled,
        )
        IconButton(
            onClick = {
                val url = service.homepageUrl
                if (url.isBlank()) {
                    Toast.makeText(context, "未配置主页链接", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "未找到可用的浏览器", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "打开主页",
            )
        }
    }
}
