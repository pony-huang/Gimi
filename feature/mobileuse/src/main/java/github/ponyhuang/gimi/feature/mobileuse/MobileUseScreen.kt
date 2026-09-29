package github.ponyhuang.gimi.feature.mobileuse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 已确认的状态卡片布局；不读取服务或发起副屏操作。 */
@Composable
fun MobileUseScreen(
    availability: MobileUseAvailability,
    textInputAvailable: Boolean,
    onPrimaryAction: () -> Unit,
    onOpenShizuku: () -> Unit,
    onTextInputAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                stringResource(R.string.mobile_use_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        stringResource(availability.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    availability.detailRes()?.let { detailRes ->
                        Text(
                            stringResource(detailRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        stringResource(
                            if (textInputAvailable) R.string.mobile_use_text_input_ready_title
                            else R.string.mobile_use_text_input_required_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!textInputAvailable) {
                        Text(
                            stringResource(R.string.mobile_use_text_input_required),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Button(onClick = onTextInputAction, modifier = Modifier.width(220.dp)) {
                Text(
                    stringResource(
                        if (textInputAvailable) R.string.mobile_use_text_input_refresh
                        else R.string.mobile_use_text_input_settings,
                    ),
                )
            }
            Button(onClick = onPrimaryAction, modifier = Modifier.width(220.dp)) {
                Text(
                    stringResource(
                        if (availability == MobileUseAvailability.PERMISSION_REQUIRED) {
                            R.string.mobile_use_authorize
                        } else {
                            R.string.mobile_use_refresh
                        },
                    ),
                )
            }
            OutlinedButton(onClick = onOpenShizuku, modifier = Modifier.width(220.dp)) {
                Text(stringResource(R.string.mobile_use_open_shizuku))
            }
        }
    }
}

private fun MobileUseAvailability.titleRes(): Int = when (this) {
    MobileUseAvailability.SHIZUKU_MISSING -> R.string.mobile_use_missing_title
    MobileUseAvailability.SHIZUKU_STOPPED -> R.string.mobile_use_stopped_title
    MobileUseAvailability.PERMISSION_REQUIRED -> R.string.mobile_use_permission_title
    MobileUseAvailability.PERMISSION_DENIED -> R.string.mobile_use_denied_title
    MobileUseAvailability.ROOT_UNSUPPORTED -> R.string.mobile_use_root_title
    MobileUseAvailability.READY -> R.string.mobile_use_ready_title
    MobileUseAvailability.BUSY -> R.string.mobile_use_busy_title
    MobileUseAvailability.UNSUPPORTED -> R.string.mobile_use_unsupported_title
}

private fun MobileUseAvailability.detailRes(): Int? = when (this) {
    MobileUseAvailability.SHIZUKU_MISSING -> R.string.mobile_use_missing_detail
    MobileUseAvailability.SHIZUKU_STOPPED -> R.string.mobile_use_stopped_detail
    MobileUseAvailability.PERMISSION_REQUIRED -> R.string.mobile_use_permission_detail
    MobileUseAvailability.PERMISSION_DENIED -> R.string.mobile_use_denied_detail
    MobileUseAvailability.ROOT_UNSUPPORTED -> R.string.mobile_use_root_detail
    MobileUseAvailability.READY -> null
    MobileUseAvailability.BUSY -> R.string.mobile_use_busy_detail
    MobileUseAvailability.UNSUPPORTED -> R.string.mobile_use_unsupported_detail
}

@Preview(showBackground = true, name = "等待授权")
@Composable
private fun PermissionPreview() {
    AsssistantaiTheme {
        MobileUseScreen(MobileUseAvailability.PERMISSION_REQUIRED, false, {}, {}, {})
    }
}

@Preview(
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "已就绪",
)
@Composable
private fun ReadyDarkPreview() {
    AsssistantaiTheme {
        MobileUseScreen(MobileUseAvailability.READY, true, {}, {}, {})
    }
}
