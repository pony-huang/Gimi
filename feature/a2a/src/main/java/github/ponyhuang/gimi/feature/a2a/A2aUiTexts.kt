package github.ponyhuang.gimi.feature.a2a

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** 远程 A2A agent 各屏共用的本地化文案片段。 */

@Composable
internal fun localizeA2aError(error: String): String = when (error) {
    "a2a.connection_error" -> stringResource(R.string.a2a_connection_error_default)
    "a2a.save_error" -> stringResource(R.string.a2a_save_error_default)
    "a2a.storage_error" -> stringResource(R.string.a2a_storage_error)
    "a2a.invalid_name" -> stringResource(R.string.a2a_invalid_name)
    "a2a.invalid_url" -> stringResource(R.string.a2a_invalid_url)
    "a2a.invalid_token" -> stringResource(R.string.a2a_invalid_token)
    "a2a.duplicate_name" -> stringResource(R.string.a2a_duplicate_name)
    else -> stringResource(R.string.a2a_connection_error_default)
}
