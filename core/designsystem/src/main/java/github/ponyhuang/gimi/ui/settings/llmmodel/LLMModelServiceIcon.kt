package github.ponyhuang.gimi.ui.settings.llmmodel

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import github.ponyhuang.gimi.ui.theme.LocalGimiDarkTheme

/**
 * 服务商品牌图标：容器完全透明，标识直接落在页面背景上，不铺任何底色块。
 *
 * 深浅可读性靠「换标识资源」而不是「换底板」：
 * - 彩色 logo（DeepSeek / MiniMax / GLM / Gemini）两种主题共用品牌原色资源；
 * - Qwen 品牌深蓝对深色背景对比度不足，深色时用 `*_night` 浅蓝渐变版；
 * - 黑色单色 logo（OpenAI / Anthropic / XiaomiMiMo）深色时用 `*_night` 反白纯色版，
 *   Kimi 深色时用品牌彩色版（蓝点 + 白 K），浅色时用全黑单色版。
 * 反白／彩色版都是纯色，不加渐变、描边或白底，保持 lobe-icons 原始图案与比例。
 *
 * 为什么不用 `res/drawable-night/`：应用内主题只在 Compose 层覆盖
 * （`GimiTheme(darkTheme = ...)`），而 `-night` 限定符按系统 `uiMode` 解析。
 * 用户手动锁定夜间、系统处于白天时夜间资源不命中，黑色 logo 会消失在深色卡片上。
 * 因此这里读 [LocalGimiDarkTheme]（应用内真实主题）显式选择 `*_night` 资源。
 */
@Composable
fun LLMModelServiceIcon(
    serviceId: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 10.dp,
) {
    val icon = modelServiceIconFor(serviceId, LocalGimiDarkTheme.current)
    Box(
        modifier = modifier.padding(contentPadding),
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
 *
 * 只有黑色单色标识与对比度不足的彩色标识需要深色变体；其余彩色标识在任何主题下
 * 共用同一份品牌原色资源。
 */
private fun modelServiceIconFor(serviceId: String, darkTheme: Boolean): Int? {
    val lightIcon = when (serviceId) {
        "deepseek" -> R.drawable.ic_model_provider_deepseek
        "minimax" -> R.drawable.ic_model_provider_minimax
        "mimo" -> R.drawable.ic_model_provider_xiaomimimo
        "openai" -> R.drawable.ic_model_provider_openai
        "anthropic" -> R.drawable.ic_model_provider_anthropic
        "gemini" -> R.drawable.ic_model_provider_gemini
        "kimi" -> R.drawable.ic_model_provider_kimi
        "glm" -> R.drawable.ic_model_provider_chatglm
        "qwen" -> R.drawable.ic_model_provider_qwen
        else -> return null
    }
    if (!darkTheme) return lightIcon
    return when (serviceId) {
        "openai" -> R.drawable.ic_model_provider_openai_night
        "anthropic" -> R.drawable.ic_model_provider_anthropic_night
        "kimi" -> R.drawable.ic_model_provider_kimi_night
        "mimo" -> R.drawable.ic_model_provider_xiaomimimo_night
        // 品牌深蓝 #082DFF 对深色背景对比度约 2.8:1，深色改用浅蓝渐变单色。
        "qwen" -> R.drawable.ic_model_provider_qwen_night
        else -> lightIcon
    }
}

@Preview(name = "单色品牌 · 浅色", showBackground = true)
@Preview(name = "单色品牌 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LLMModelServiceIconThemePreview() {
    GimiTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("openai", "anthropic", "kimi", "mimo", "qwen").forEach { serviceId ->
                LLMModelServiceIcon(serviceId = serviceId, modifier = Modifier.size(48.dp))
            }
        }
    }
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
