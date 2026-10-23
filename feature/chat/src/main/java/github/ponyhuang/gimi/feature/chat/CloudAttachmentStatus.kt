package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import github.ponyhuang.gimi.domain.conversation.cloud.CloudAttachmentState
import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileFailure

/** 文件状态只使用枚举对应的本地化文案，不显示 SDK 响应、路径或凭据。 */
@Composable
internal fun cloudAttachmentStatus(state: CloudAttachmentState, active: Boolean): String = stringResource(when (state) {
    CloudAttachmentState.Waiting -> R.string.chat_cloud_waiting
    CloudAttachmentState.Preparing -> R.string.chat_cloud_preparing
    CloudAttachmentState.Uploading -> R.string.chat_cloud_uploading
    is CloudAttachmentState.Ready -> R.string.chat_cloud_upload_ready
    CloudAttachmentState.Cancelled -> if (active) R.string.chat_cloud_file_cancelled_waiting else R.string.chat_cloud_file_cancelled
    is CloudAttachmentState.Failed -> when (state.reason) {
        CloudFileFailure.UnverifiedChatReference -> R.string.chat_cloud_reference_unverified
        CloudFileFailure.Disabled -> R.string.chat_cloud_upload_disabled
        CloudFileFailure.InvalidConfiguration -> R.string.chat_cloud_upload_configuration
        CloudFileFailure.UnsupportedProvider -> R.string.chat_cloud_upload_provider
        CloudFileFailure.LocalFileUnavailable -> R.string.chat_cloud_upload_local_missing
        CloudFileFailure.StorageUnavailable -> R.string.chat_cloud_upload_storage
        CloudFileFailure.InvalidResponse -> R.string.chat_cloud_upload_response
        CloudFileFailure.FileTooLarge -> R.string.chat_cloud_file_too_large
        CloudFileFailure.UploadRejected -> R.string.chat_cloud_upload_failed
    }
})

internal val CloudAttachmentState.canRetry: Boolean get() = this == CloudAttachmentState.Cancelled ||
    this is CloudAttachmentState.Failed && reason !in setOf(CloudFileFailure.UnverifiedChatReference,
        CloudFileFailure.Disabled, CloudFileFailure.InvalidConfiguration, CloudFileFailure.UnsupportedProvider)
