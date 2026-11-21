package github.ponyhuang.gimi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material Design 3 统一圆角令牌：
 * - `extraSmall` (4dp)：徽章、紧凑角标、Snackbar
 * - `small` (8dp)：菜单、小标签、内嵌代码块
 * - `medium` (12dp)：OutlinedTextField 表单输入框、普通内嵌卡片
 * - `large` (16dp)：设置分组卡片（PreferenceGroupCard）、单选行卡片（GimiPickerItemRow）、消息气泡
 * - `extraLarge` (28dp)：模态底部抽屉（GimiSheetBase）、弹窗、底部聊天输入胶囊
 */
val GimiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
