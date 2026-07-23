package github.ponyhuang.asssistantai.assistant

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import github.ponyhuang.asssistantai.R

/** 通过系统弹窗引导用户把“语音助手”磁贴加入快捷设置。 */
fun requestAddAssistantTile(context: Context) {
    val statusBarManager = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBarManager.requestAddTileService(
        ComponentName(context, AssistantTileService::class.java),
        context.getString(R.string.assistant_tile_label),
        Icon.createWithResource(context, R.mipmap.ic_launcher),
        ContextCompat.getMainExecutor(context),
        { },
    )
}
