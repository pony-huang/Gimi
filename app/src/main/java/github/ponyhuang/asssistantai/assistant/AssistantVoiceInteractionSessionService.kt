package github.ponyhuang.asssistantai.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View

class AssistantVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        AssistantVoiceInteractionSession(this)
}

/** 最薄会话：不读取 Assist 内容，直接唤起统一的透明助理 Activity。 */
private class AssistantVoiceInteractionSession(
    context: Context,
) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View = View(context)

    override fun onShow(args: Bundle?, showFlags: Int) {
        val intent = Intent(context, AssistantOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(
                AssistantOverlayActivity.EXTRA_INVOCATION_SOURCE,
                AssistantOverlayActivity.SOURCE_SYSTEM_GESTURE,
            )
        context.startActivity(intent)
        finish()
    }
}
