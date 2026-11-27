package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.a2ui.compose.ui.A2uiCatalog
import github.ponyhuang.gimi.feature.chat.a2ui.components.chatA2uiCustomCatalog

/** A2UI 0.9.1 的每个 surface 使用一个目录，因此由宿主发布可混用的联合契约。 */
internal fun extendedChatA2uiCatalog(basic: A2uiCatalog): A2uiCatalog = A2uiCatalog(
    catalogId = "https://gimi.app/a2ui/catalogs/chat/v2",
    components = basic.components + chatA2uiCustomCatalog.components,
    functions = basic.functions,
    themeSchema = basic.themeSchema,
    isInline = true,
)
