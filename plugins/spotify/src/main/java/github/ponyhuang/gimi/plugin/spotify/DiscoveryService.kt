package github.ponyhuang.gimi.plugin.spotify

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 无功能发现标记：宿主通过 queryIntentServices 发现本插件，无需实现。 */
class DiscoveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
