package github.ponyhuang.gimi.plugin.xiaohongshu

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 仅供宿主发现插件 APK 的无功能标记服务。 */
class DiscoveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
