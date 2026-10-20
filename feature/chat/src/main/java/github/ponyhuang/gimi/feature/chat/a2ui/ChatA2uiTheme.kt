package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 仅作用于生成卡片的紧凑字号；保留应用字体、主题配色和系统字号缩放。 */
@Composable
internal fun ChatA2uiTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    MaterialTheme(
        typography = base.copy(
            displayLarge = base.displayLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
            displayMedium = base.displayMedium.copy(fontSize = 24.sp, lineHeight = 30.sp),
            displaySmall = base.displaySmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
            headlineLarge = base.headlineLarge.copy(fontSize = 24.sp, lineHeight = 30.sp),
            headlineMedium = base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp),
            headlineSmall = base.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp),
            titleLarge = base.titleLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = base.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = base.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
            labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
            labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        ),
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(8.dp),
        ),
        content = content,
    )
}
