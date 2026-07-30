package github.ponyhuang.gimi.feature.chat

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun rememberSpeechRecognizerController(
    onPartialResult: ((String) -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    onFinalResult: (String) -> Unit,
): SpeechRecognizerController {
    val isPreview = LocalInspectionMode.current
    val activity = LocalActivity.current
    val context = LocalContext.current

    val controller = if (isPreview) {
        remember { object : SpeechRecognizerController {} }
    } else {
        viewModel(
            modelClass = SpeechRecognizerControllerViewModel::class.java,
            viewModelStoreOwner = activity?.getViewModelStoreOwner() // Prioritize activity scope which supports configuration changes
                ?: checkNotNull(LocalViewModelStoreOwner.current) { // Fallback to the default store
                    "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
                },
            factory = SpeechRecognizerControllerViewModel.Factory(context),
        ).apply {
            setCallbacks(
                onPartialResult = onPartialResult,
                onFinalResult = onFinalResult,
                onError = onError,
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep the recognizer across configuration changes; release on navigation/real disposal
            if (activity?.isChangingConfigurations != true) {
                controller.release()
            }
        }
    }

    return controller
}

private fun Activity.getViewModelStoreOwner(): ViewModelStoreOwner? =
    (this as? ComponentActivity)?.let {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = it.viewModelStore
        }
    }
