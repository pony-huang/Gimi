package github.ponyhuang.gimi.feature.assistant.voicewake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.model.WakePhraseError
import github.ponyhuang.gimi.feature.assistant.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun VoiceWakeSettingsScreen(
    state: VoiceWakeSettingsUiState,
    onAction: (VoiceWakeSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Mic,
                        title = stringResource(R.string.voicewake_enable_title),
                        subtitle = listeningSubtitle(state),
                        trailingContent = {
                            Switch(
                                checked = state.voiceState.enabled,
                                enabled = state.voiceState.enabled ||
                                    (state.voiceState.recognizerAvailable && state.configurationReady),
                                onCheckedChange = {
                                    onAction(VoiceWakeSettingsAction.ToggleListening(it))
                                },
                            )
                        },
                    )
                }
            }

            if (!state.voiceState.recognizerAvailable) {
                item {
                    PreferenceBanner(
                        text = stringResource(R.string.voicewake_recognizer_unavailable),
                        tone = PreferenceBannerTone.Error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else if (!state.configurationReady) {
                item {
                    PreferenceBanner(
                        text = stringResource(R.string.voicewake_chat_model_required),
                        tone = PreferenceBannerTone.Error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item { PreferenceSectionTitle(stringResource(R.string.voicewake_section_recognition)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.RecordVoiceOver,
                        title = stringResource(R.string.voicewake_recognition_title),
                        subtitle = stringResource(R.string.voicewake_recognition_subtitle),
                    )
                }
            }

            item { PreferenceSectionTitle(stringResource(R.string.voicewake_section_phrases)) }
            item {
                PreferenceGroupCard {
                    state.voiceState.triggerPhrases.forEach { phrase ->
                        PreferenceListItem(
                            icon = Icons.Default.RecordVoiceOver,
                            title = phrase,
                            subtitle = stringResource(R.string.voicewake_phrase_saved),
                            showDivider = true,
                            trailingContent = {
                                IconButton(
                                    enabled = state.voiceState.triggerPhrases.size > 1,
                                    onClick = {
                                        onAction(VoiceWakeSettingsAction.RemovePhrase(phrase))
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.voicewake_remove_phrase,
                                            phrase,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.voicewake_phrase_explanation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.phraseDraft,
                            onValueChange = {
                                onAction(VoiceWakeSettingsAction.PhraseChanged(it))
                            },
                            label = { Text(stringResource(R.string.voicewake_phrase_label)) },
                            supportingText = {
                                Text(phraseSupportingText(state.phraseError))
                            },
                            isError = state.phraseError != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { onAction(VoiceWakeSettingsAction.AddPhrase) },
                            enabled = state.phraseDraft.isNotBlank() && state.phraseError == null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(
                                stringResource(R.string.voicewake_add_phrase),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun listeningSubtitle(state: VoiceWakeSettingsUiState): String = when {
    !state.voiceState.recognizerAvailable ->
        stringResource(R.string.voicewake_status_unavailable)
    !state.voiceState.enabled -> stringResource(R.string.voicewake_status_disabled)
    state.voiceState.status == VoiceWakeStatus.Listening ->
        stringResource(R.string.voicewake_status_listening)
    state.voiceState.status == VoiceWakeStatus.Submitting ->
        stringResource(R.string.voicewake_status_submitting)
    else -> stringResource(R.string.voicewake_status_waiting_foreground)
}

@Composable
private fun phraseSupportingText(error: WakePhraseError?): String = when (error) {
    WakePhraseError.InvalidLength -> stringResource(R.string.voicewake_phrase_error_length)
    WakePhraseError.InvalidCharacters -> stringResource(R.string.voicewake_phrase_error_characters)
    WakePhraseError.Duplicate -> stringResource(R.string.voicewake_phrase_error_duplicate)
    WakePhraseError.LastPhrase -> stringResource(R.string.voicewake_phrase_error_last)
    null -> stringResource(R.string.voicewake_phrase_hint)
}

@Preview(name = "语音唤醒 · 浅色", showBackground = true, widthDp = 412, heightDp = 820)
@Composable
private fun VoiceWakeSettingsLightPreview() {
    AsssistantaiTheme(darkTheme = false) {
        VoiceWakeSettingsScreen(
            state = VoiceWakeSettingsUiState(
                voiceState = VoiceWakeState(
                    enabled = true,
                    recognizerAvailable = true,
                    status = VoiceWakeStatus.Listening,
                    triggerPhrases = listOf("吉米", "Hey Gimi"),
                ),
                configurationReady = true,
            ),
            onAction = {},
        )
    }
}

@Preview(name = "语音唤醒 · 深色不可用", showBackground = true, widthDp = 412, heightDp = 820)
@Composable
private fun VoiceWakeSettingsDarkPreview() {
    AsssistantaiTheme(darkTheme = true) {
        VoiceWakeSettingsScreen(
            state = VoiceWakeSettingsUiState(
                voiceState = VoiceWakeState(recognizerAvailable = false),
                configurationReady = true,
                phraseDraft = "小助手",
            ),
            onAction = {},
        )
    }
}
