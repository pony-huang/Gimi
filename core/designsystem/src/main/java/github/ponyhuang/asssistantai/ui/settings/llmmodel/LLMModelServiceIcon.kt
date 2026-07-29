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
import github.ponyhuang.asssistantai.core.designsystem.R

/**
 * Consistent, brand-faithful icon treatment for model service surfaces.
 *
 * 图标资源来自 lobe-icons（SVG 转 vector drawable）：
 * - 有 `-color` 彩色版且浅色背景可辨的服务（DeepSeek / MiniMax / ChatGLM）
 *   统一使用彩色版，深浅色模式通用，只放 `res/drawable/`；
 * - 其余服务（OpenAI / Anthropic / XiaomiMiMo / Kimi）保留单色：
 *   lobe-icons 无其彩色版，或彩色版为白色主体（Kimi 白 K）在浅色背景下不可见；
 *   `res/drawable/` 为浅色模式用，`res/drawable-night/` 为深色模式反白/彩色版。
 * 系统按夜间模式自动切换资源，调用点无需关心主题。
 */
@Composable
fun LLMModelServiceIcon(
    serviceId: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 10.dp,
) {
    val icon = modelServiceIconFor(serviceId)
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

private fun modelServiceIconFor(serviceId: String): Int? = when (serviceId) {
    "deepseek" -> R.drawable.ic_model_provider_deepseek
    "minimax" -> R.drawable.ic_model_provider_minimax
    "mimo" -> R.drawable.ic_model_provider_xiaomimimo
    "openai" -> R.drawable.ic_model_provider_openai
    "anthropic" -> R.drawable.ic_model_provider_anthropic
    "kimi" -> R.drawable.ic_model_provider_kimi
    "glm" -> R.drawable.ic_model_provider_chatglm
    else -> null
}
