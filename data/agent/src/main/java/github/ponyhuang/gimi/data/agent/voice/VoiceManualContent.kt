package github.ponyhuang.gimi.data.agent.voice

import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FileData
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.conversation.model.VendorFileReference

/** 级联与聊天共用归档引用，媒体解析继续交给模型访问层。 */
internal fun voiceManualContent(text: String, attachments: List<FileAttachment>): Content = Content(
    role = Role.USER,
    parts = buildList {
        text.takeIf(String::isNotBlank)?.let { add(Part(text = it)) }
        attachments.forEach { attachment ->
            val vendorUri = attachment.fileVendor?.let { vendor ->
                attachment.fileId?.let { VendorFileReference.uri(vendor, it) }
            }
            if (attachment.inlineData != null) {
                add(Part(inlineData = com.google.adk.kt.types.Blob(
                    mimeType = attachment.mimeType,
                    data = attachment.inlineData,
                    displayName = attachment.displayName.takeIf(String::isNotBlank),
                )))
            } else {
                add(Part(fileData = FileData(
                    mimeType = attachment.mimeType,
                    displayName = attachment.displayName,
                    fileUri = vendorUri ?: requireNotNull(attachment.payloadReference),
                )))
            }
        }
    },
)
