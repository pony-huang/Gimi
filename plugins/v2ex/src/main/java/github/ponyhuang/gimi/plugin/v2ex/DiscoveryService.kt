package github.ponyhuang.gimi.plugin.v2ex

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 无功能的发现标记组件 — 仅供宿主 `queryIntentServices` 发现本插件 APK，不做任何事。
 */
class DiscoveryService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null
}
