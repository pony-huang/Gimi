package github.ponyhuang.gimi.feature.a2a

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** 远程 A2A agent 各屏共用的本地化文案片段。 */

@Composable
internal fun localizeA2aError(error: String): String = when (error) {
    "a2a.connection_error" -> stringResource(R.string.a2a_connection_error_default)
    "a2a.save_error" -> stringResource(R.string.a2a_save_error_default)
    else -> error
}
