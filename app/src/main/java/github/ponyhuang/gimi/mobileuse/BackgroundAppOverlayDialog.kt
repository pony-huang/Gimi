package github.ponyhuang.gimi.mobileuse

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** 服务弹窗在 show 前明确设置悬浮类型，复用窗口宿主的生命周期和 Compose 主题。 */
@Composable
internal fun BackgroundAppOverlayDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val parent = LocalView.current
    val composition = rememberCompositionContext()
    val currentContent by rememberUpdatedState(content)
    val dismiss by rememberUpdatedState(onDismiss)
    val dialog = remember(context, parent) {
        Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCanceledOnTouchOutside(true)
        }
    }
    val view = remember(dialog, composition) {
        ComposeView(context).apply {
            setViewTreeLifecycleOwner(parent.findViewTreeLifecycleOwner())
            setViewTreeViewModelStoreOwner(parent.findViewTreeViewModelStoreOwner())
            setViewTreeSavedStateRegistryOwner(parent.findViewTreeSavedStateRegistryOwner())
            setParentCompositionContext(composition)
            setContent {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    currentContent()
                }
            }
        }
    }
    DisposableEffect(dialog, view) {
        dialog.setContentView(view)
        dialog.setOnCancelListener { dismiss() }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        onDispose {
            dialog.dismiss()
            view.disposeComposition()
        }
    }
}
