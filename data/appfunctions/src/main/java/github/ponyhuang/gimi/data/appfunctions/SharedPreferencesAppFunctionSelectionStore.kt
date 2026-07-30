package github.ponyhuang.gimi.data.appfunctions

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionSelection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
internal class SharedPreferencesAppFunctionSelectionStore @Inject constructor(
    @ApplicationContext context: Context,
) : AppFunctionSelectionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableSelection = MutableStateFlow(readSelection())

    override val selection: StateFlow<AppFunctionSelection> = mutableSelection.asStateFlow()

    override fun update(selection: AppFunctionSelection) {
        preferences.edit {
            putBoolean(KEY_FEATURE_ENABLED, selection.featureEnabled)
            putStringSet(
                KEY_ENABLED_PACKAGES,
                selection.enabledPackageNames.toMutableSet(),
            )
            putStringSet(
                KEY_ENABLED_FUNCTIONS,
                selection.enabledFunctionKeys.mapTo(mutableSetOf(), AppFunctionKey::encoded),
            )
        }
        mutableSelection.value = selection
    }

    private fun readSelection(): AppFunctionSelection = AppFunctionSelection(
        featureEnabled = preferences.getBoolean(KEY_FEATURE_ENABLED, false),
        enabledPackageNames = preferences
            .getStringSet(KEY_ENABLED_PACKAGES, emptySet())
            .orEmpty()
            .toSet(),
        enabledFunctionKeys = preferences
            .getStringSet(KEY_ENABLED_FUNCTIONS, emptySet())
            .orEmpty()
            .mapNotNullTo(linkedSetOf(), AppFunctionKey::decode),
    )

    private companion object {
        const val PREFERENCES_NAME = "appfunctions_experimental"
        const val KEY_FEATURE_ENABLED = "feature_enabled_v1"
        const val KEY_ENABLED_PACKAGES = "enabled_packages_v1"
        const val KEY_ENABLED_FUNCTIONS = "enabled_functions_v1"
    }
}
