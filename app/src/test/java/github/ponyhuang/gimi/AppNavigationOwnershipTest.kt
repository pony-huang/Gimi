package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationOwnershipTest {

    @Test
    fun appNavigationDoesNotOwnChatStateOrFileOpening() {
        val navigation = File(
            "src/main/java/github/ponyhuang/gimi/app/navigation/AppNavigation.kt",
        )

        assertTrue("AppNavigation.kt should exist", navigation.isFile)
        val content = navigation.readText()
        listOf(
            "ChatViewModel",
            "ChatAction",
            "ChatDrawer",
            "LocalFileReference",
            "ACTION_VIEW",
        ).forEach { forbiddenSymbol ->
            assertFalse(
                "$forbiddenSymbol belongs to feature:chat, not the app navigation root",
                content.contains(forbiddenSymbol),
            )
        }
    }

    @Test
    fun appNavigationComposesFeatureEntryProvidersInsteadOfFeatureRoutes() {
        val navigation = File(
            "src/main/java/github/ponyhuang/gimi/app/navigation/AppNavigation.kt",
        )

        assertTrue("AppNavigation.kt should exist", navigation.isFile)
        val content = navigation.readText()
        assertFalse(
            "Feature Route composables must be resolved by their owning entry provider",
            Regex("import github\\.ponyhuang\\.gimi\\.feature\\..*Route").containsMatchIn(content),
        )
        assertFalse(
            "The app root must not own the destination dispatch table",
            content.contains("when (route)"),
        )
        assertFalse(
            "Feature destinations must be owned by feature modules",
            content.contains("AppRoute"),
        )
        assertTrue(
            "The app root should assemble feature entry providers",
            content.contains("EntryProvider"),
        )
    }
}
