package github.ponyhuang.gimi.ui.settings.llmmodel

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.core.designsystem.R
import github.ponyhuang.gimi.ui.theme.GimiTheme

/**
 * Consistent, brand-faithful icon treatment for model service surfaces.
 *
 * 图标资源来自 lobe-icons（SVG 转 vector drawable），按可辨性分三类：
 * - 有 `-color` 彩色版且深浅色背景均可辨的服务（DeepSeek / MiniMax / ChatGLM / Gemini）
 *   统一使用彩色版，深浅色模式通用，只放 `res/drawable/`；
 * - 彩色版在浅色背景下可辨、但在深色背景下不可辨（Qwen 深蓝主体）：
 *   `res/drawable/` 放彩色版，`res/drawable-night/` 放深色模式用的浅色渐变版；
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

/**
 * 服务模板标识 → 图标资源映射；core 模块不能反向依赖 data 模块，故以字面量维护。
 * 新增厂商模板时，须同步此处与 `data/modelcatalog` 的 ModelServiceTemplates 清单，
 * 否则将静默回退到 SmartToy 占位图标。
 */
private fun modelServiceIconFor(serviceId: String): Int? = when (serviceId) {
    "deepseek" -> R.drawable.ic_model_provider_deepseek
    "minimax" -> R.drawable.ic_model_provider_minimax
    "mimo" -> R.drawable.ic_model_provider_xiaomimimo
    "openai" -> R.drawable.ic_model_provider_openai
    "anthropic" -> R.drawable.ic_model_provider_anthropic
    "gemini" -> R.drawable.ic_model_provider_gemini
    "kimi" -> R.drawable.ic_model_provider_kimi
    "glm" -> R.drawable.ic_model_provider_chatglm
    "qwen" -> R.drawable.ic_model_provider_qwen
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun LLMModelServiceIconKnownServicePreview() {
    GimiTheme {
        LLMModelServiceIcon(serviceId = "deepseek", modifier = Modifier.size(48.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun LLMModelServiceIconFallbackPreview() {
    GimiTheme {
        LLMModelServiceIcon(serviceId = "unknown", modifier = Modifier.size(48.dp))
    }
}

@Preview(showBackground = true)
@Preview(name = "深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LLMModelServiceIconQwenPreview() {
    GimiTheme {
        LLMModelServiceIcon(serviceId = "qwen", modifier = Modifier.size(48.dp))
    }
}
