package github.ponyhuang.gimi.environment.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 安装与本地执行由独立 UID 的前台服务托管，通知可停止整个环境。 */
class RuntimeService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("environment", getString(R.string.runtime_name), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(this, 0, Intent(this, RuntimeService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, "environment").setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(getString(R.string.runtime_name)).setContentText(getString(R.string.runtime_active))
            .addAction(Notification.Action.Builder(null, getString(R.string.runtime_stop), stop).build()).setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                "stop" -> { RuntimeEngine.get(this).disable(); stopSelf() }
                "install" -> RuntimeEngine.get(this).install()
                "enable" -> RuntimeEngine.get(this).enable()
            }
        } catch (error: IllegalStateException) {
            // 预登记与执行间状态漂移（如未安装即启用）：忽略，通知保留可手动停止。
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { RuntimeEngine.get(this).disable(); super.onDestroy() }
}
