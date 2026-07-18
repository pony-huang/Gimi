package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import android.provider.MediaStore
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

/** Opens a system camera after explicit confirmation. */
@Singleton
class CameraTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "capture_photo", description = "Opens the system camera to take a photo.") fun capturePhoto(): Map<String, Any> = queue.request(
        title = "Take photo", summary = "Open the system camera to take a photo.",
        intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    )

    @Tool(name = "capture_video", description = "Opens the system camera to record a video.") fun captureVideo(): Map<String, Any> = queue.request(
        title = "Record video", summary = "Open the system camera to record a video.",
        intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE),
    )
}
