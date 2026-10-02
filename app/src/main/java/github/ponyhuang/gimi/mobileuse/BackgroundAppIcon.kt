package github.ponyhuang.gimi.mobileuse

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import github.ponyhuang.gimi.R

/** 使用真实应用图标；feature 气泡只接收图标内容，不依赖 app 资源。 */
@Composable
internal fun BackgroundAppIcon() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val size = with(LocalDensity.current) { 56.dp.roundToPx() }
    val bitmap = remember(context, configuration, size) {
        // 启动图标可能是 AdaptiveIconDrawable；先按 Android Drawable 规则绘制，避免 painterResource 解析 XML 闪退。
        requireNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))
            .toBitmap(width = size, height = size).asImageBitmap()
    }
    Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize())
}
