package github.ponyhuang.gimi.feature.assistant.voicewake

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations owned by voice-wake settings. */
sealed interface VoiceWakeDestination : NavKey {
    /** Voice-wake settings destination. */
    @Serializable
    data object Settings : VoiceWakeDestination
}

/** Resolves voice-wake-owned destinations. */
@Composable
fun VoiceWakeEntryProvider(destination: NavKey, onBack: () -> Unit): Boolean =
    when (destination) {
        VoiceWakeDestination.Settings -> {
            VoiceWakeSettingsRoute(onBack = onBack)
            true
        }

        else -> false
    }
