package github.ponyhuang.asssistantai.ui.settings.llmmodel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.data.DefaultModelServices

/**
 * Consistent, brand-faithful icon treatment for model service surfaces.
 *
 * 图标来源：[DefaultModelServices] 按 [serviceId] 查表得到 `iconRes`。
 * 新增厂商只需在 [DefaultModelServices] 给对应 [github.ponyhuang.asssistantai.data.LLMModelProvider]
 * 配 `iconRes`，本组件无需任何改动即可渲染品牌图标。
 */
@Composable
fun LLMModelServiceIcon(
    serviceId: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 10.dp,
) {
    val icon = DefaultModelServices.iconFor(serviceId)
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}