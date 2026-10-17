package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.A2uiSurface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.chat.R
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 无状态 surface 宿主；模型和交互生命周期由聊天 ViewModel 管理。 */
@Composable
internal fun ChatA2uiContent(surfaces: List<A2uiSurfaceModel>, failed: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        surfaces.forEach { surface ->
            A2uiSurface(
                surfaceModel = surface,
                modifier = Modifier.fillMaxWidth(),
                loadingContent = { CircularProgressIndicator() },
                errorContent = { A2uiError() },
            )
        }
        if (failed) A2uiError()
    }
}

@Composable
private fun A2uiError() {
    Text(stringResource(R.string.chat_a2ui_failed), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun A2uiErrorPreview() {
    AsssistantaiTheme { ChatA2uiContent(emptyList(), failed = true) }
}
