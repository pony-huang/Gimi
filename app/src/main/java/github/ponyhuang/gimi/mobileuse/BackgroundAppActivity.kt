package github.ponyhuang.gimi.mobileuse

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.data.mobileuse.MobileDisplayPreviewGateway
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.appearance.ThemeMode
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.feature.mobileuse.BackgroundAppViewModel
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import javax.inject.Inject

/** 全屏只承载预览，不能抢走副屏目标 App 的原生输入连接。 */
@AndroidEntryPoint
class BackgroundAppActivity : ComponentActivity() {
    @Inject lateinit var gateway: MobileDisplayPreviewGateway
    @Inject lateinit var host: BackgroundAppWindowHost
    @Inject lateinit var repository: MobileUseRepository
    @Inject lateinit var appearance: AppearanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (repository.displaySession.value?.id != intent.getStringExtra(BackgroundAppWindowHost.SESSION_ID)) { finish(); return }
        host.activateFullscreen(checkNotNull(repository.displaySession.value).id)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge()
        setContent {
            val mode by appearance.themeMode.collectAsStateWithLifecycle()
            val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
            AsssistantaiTheme(darkTheme = dark) {
                val presentation by host.presentation.collectAsStateWithLifecycle()
                LaunchedEffect(presentation) { if (presentation != BackgroundAppPresentation.FULLSCREEN) finish() }
                BackgroundAppContent(hiltViewModel<BackgroundAppViewModel>(), gateway, host, small = false,
                    modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)), onCollapsed = ::finish)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && host.presentation.value == BackgroundAppPresentation.FULLSCREEN) host.collapse()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra(BackgroundAppWindowHost.SESSION_ID)
        if (id == null || repository.displaySession.value?.id != id) finish()
        else host.activateFullscreen(id)
    }
}
