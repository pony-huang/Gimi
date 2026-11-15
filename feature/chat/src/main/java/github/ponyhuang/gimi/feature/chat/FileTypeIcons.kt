package github.ponyhuang.gimi.feature.chat

import androidx.annotation.DrawableRes

/**
 * 文件类型 → 彩色图标的映射（vscode-icons 平涂资产，日夜主题共用一套，不做反色）。
 *
 * chip、抽屉"上传的文件"列表与气泡附件行三处共用，保证同一文件在所有界面图标一致。
 * 未知类型回退纯文本文件图标；代码文件（py/js/kt 等）与 txt/md 同用文本图标。
 */
@DrawableRes
internal fun fileTypeIconRes(mimeType: String): Int = when (mimeType.substringBefore(';').trim().lowercase()) {
    "application/pdf" -> R.drawable.ic_file_pdf
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    -> R.drawable.ic_file_word
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    -> R.drawable.ic_file_ppt
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "text/csv",
    -> R.drawable.ic_file_excel
    "application/json" -> R.drawable.ic_file_json
    "application/epub+zip",
    "application/x-mobipocket-ebook",
    -> R.drawable.ic_file_ebook
    "image/jpeg",
    "image/png",
    "image/gif",
    "image/webp",
    "image/bmp",
    "image/tiff",
    -> R.drawable.ic_file_image
    else -> R.drawable.ic_file_text
}
