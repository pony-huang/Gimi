package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

@Composable
fun ViewModelStore(
    vararg keys: Any?,
    content: @Composable () -> Unit,
) {
    val viewModelStore = remember { androidx.lifecycle.ViewModelStore() }
    val viewModelStoreOwner = remember(viewModelStore) {
        object : ViewModelStoreOwner {
            override val viewModelStore: androidx.lifecycle.ViewModelStore = viewModelStore
        }
    }

    DisposableEffect(*keys) {
        onDispose {
            viewModelStore.clear()
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
        content()
    }
}
