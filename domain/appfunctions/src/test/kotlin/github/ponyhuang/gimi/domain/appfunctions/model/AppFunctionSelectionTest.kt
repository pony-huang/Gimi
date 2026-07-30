package github.ponyhuang.gimi.domain.appfunctions.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFunctionSelectionTest {

    @Test
    fun newFunctionsRemainDisabledUntilExplicitlySelected() {
        val existing = AppFunctionKey("notes.app", "create_note")
        val newlyDiscovered = AppFunctionKey("notes.app", "archive_note")
        val selection = AppFunctionSelection(
            featureEnabled = true,
            enabledPackageNames = setOf(existing.packageName),
            enabledFunctionKeys = setOf(existing),
        )

        assertTrue(selection.isEnabled(existing))
        assertFalse(selection.isEnabled(newlyDiscovered))
    }

    @Test
    fun disablingFeatureMakesEveryFunctionUnavailableWithoutForgettingSelection() {
        val function = AppFunctionKey("notes.app", "create_note")
        val selection = AppFunctionSelection(
            featureEnabled = false,
            enabledPackageNames = setOf(function.packageName),
            enabledFunctionKeys = setOf(function),
        )

        assertFalse(selection.isEnabled(function))
        assertTrue(function in selection.enabledFunctionKeys)
    }

    @Test
    fun functionRequiresIndependentApplicationAndFunctionSelection() {
        val function = AppFunctionKey("notes.app", "create_note")

        assertFalse(
            AppFunctionSelection(
                featureEnabled = true,
                enabledFunctionKeys = setOf(function),
            ).isEnabled(function),
        )
        assertFalse(
            AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(function.packageName),
            ).isEnabled(function),
        )
        assertTrue(
            AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(function.packageName),
                enabledFunctionKeys = setOf(function),
            ).isEnabled(function),
        )
    }

    @Test
    fun appToggleOnlyChangesFunctionsPresentInCurrentCatalog() {
        val first = AppFunctionKey("notes.app", "create_note")
        val second = AppFunctionKey("notes.app", "edit_note")
        val otherApp = AppFunctionKey("calendar.app", "create_event")
        val selection = AppFunctionSelection(
            featureEnabled = true,
            enabledPackageNames = setOf(otherApp.packageName),
            enabledFunctionKeys = setOf(otherApp),
        )

        val enabled = selection.setAppEnabled(
            packageName = "notes.app",
            availableFunctions = setOf(first, second),
            enabled = true,
        )
        val disabled = enabled.setAppEnabled(
            packageName = "notes.app",
            availableFunctions = setOf(first, second),
            enabled = false,
        )

        assertTrue(enabled.isEnabled(first))
        assertTrue(enabled.isEnabled(second))
        assertTrue(enabled.isEnabled(otherApp))
        assertFalse(disabled.isEnabled(first))
        assertFalse(disabled.isEnabled(second))
        assertTrue(disabled.isEnabled(otherApp))
    }
}
