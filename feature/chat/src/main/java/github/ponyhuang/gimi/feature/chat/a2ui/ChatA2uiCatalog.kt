package github.ponyhuang.gimi.feature.chat.a2ui

import android.icu.text.MessageFormat
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.model.catalog.basiccatalog.createBasicCatalogFunctions
import androidx.a2ui.model.catalog.functions.A2uiLocaleProvider
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import coil3.compose.AsyncImage

/** 聊天宿主的实际目录；导出的 schema 同时作为子 Agent 的能力描述。 */
internal fun chatA2uiCatalog(openUrl: (String) -> Unit): A2uiCatalog {
    val defaults = MaterialA2uiBasicCatalogV1Defaults
    return A2uiCatalog(
        catalogId = "https://gimi.app/a2ui/catalogs/chat/v1",
        components = listOf(
            ChatA2uiText, defaults.icon, defaults.row, defaults.column, ChatA2uiCard, ChatA2uiList,
            defaults.divider, ChatA2uiButton, defaults.textField, defaults.checkBox, defaults.choicePicker,
            defaults.slider, defaults.dateTimeInput,
            defaults.image { url, description, scale, modifier, onError ->
                AsyncImage(model = url, contentDescription = description, contentScale = scale,
                    modifier = modifier, onError = { onError(it.result.throwable) })
            },
            defaults.video(ChatA2uiVideoRenderer),
            defaults.audioPlayer(ChatA2uiAudioPlayerRenderer),
        ),
        functions = createBasicCatalogFunctions(
            urlOpener = { openUrl(it) },
            messageFormatter = { pattern, locale, arguments -> MessageFormat(pattern, locale).format(arguments) },
            localeProvider = A2uiLocaleProvider.Default,
        ),
        isInline = true,
    )
}
