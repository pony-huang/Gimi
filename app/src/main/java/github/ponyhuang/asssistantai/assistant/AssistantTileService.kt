package github.ponyhuang.asssistantai.assistant

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.TileService

/** 快捷设置磁贴入口：唤起透明助理浮层；锁屏下先走系统解锁流程。 */
class AssistantTileService : TileService() {

    override fun onClick() {
        val intent = Intent(this, AssistantOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (isLocked) {
            unlockAndRun { startActivityAndCollapse(pendingIntent) }
        } else {
            startActivityAndCollapse(pendingIntent)
        }
    }
}
