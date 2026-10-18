package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** A2UI 媒体渲染器的离线预览；样本 URL 来自 Media3 官方测试媒体桶。 */
@Preview(showBackground = true, heightDp = 640)
@Preview(
    showBackground = true,
    heightDp = 640,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun A2uiMediaPreview() {
    AsssistantaiTheme {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("A2UI 媒体组件")
            ChatA2uiVideoRenderer.Video(
                url = "https://storage.googleapis.com/exoplayer-test-media-0/shortform_2.mp4",
                modifier = Modifier.fillMaxWidth(),
                onError = {},
            )
            ChatA2uiAudioPlayerRenderer.AudioPlayer(
                url = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                contentDescription = "Media3 测试音频",
                modifier = Modifier.fillMaxWidth(),
                onError = {},
            )
        }
    }
}
