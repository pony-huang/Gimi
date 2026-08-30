package github.ponyhuang.gimi.feature.skills

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by skill settings. */
sealed interface SkillsDestination : NavKey {
    /** Installed-skills settings destination. */
    @Serializable
    data object Settings : SkillsDestination
}

/** Resolves skills-owned destinations. */
@Composable
fun SkillsEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        SkillsDestination.Settings -> {
            SkillsSettingsRoute(onBack = onBack)
            true
        }

        else -> false
    }
