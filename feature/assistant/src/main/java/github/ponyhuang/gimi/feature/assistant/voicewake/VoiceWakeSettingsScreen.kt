package github.ponyhuang.gimi.feature.assistant.voicewake

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.WakeKeywordError
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import github.ponyhuang.gimi.domain.speech.model.WakeModelInfo
import github.ponyhuang.gimi.domain.speech.model.WakeModelSource
import github.ponyhuang.gimi.domain.speech.model.WakeModelState
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import github.ponyhuang.gimi.domain.speech.model.normalizeWakeKeyword
import github.ponyhuang.gimi.feature.assistant.R
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun VoiceWakeSettingsScreen(
    state: VoiceWakeSettingsUiState,
    onAction: (VoiceWakeSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    overlayPermissionGranted: Boolean = false,
    onOpenOverlaySettings: () -> Unit = {},
) {
    if (state.showUnsavedChangesDialog) {
        UnsavedKeywordDialog(onAction)
    }
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.BluetoothAudio,
                        title = stringResource(R.string.voicewake_listening_title),
                        subtitle = if (state.isApplyingKeyword) {
                            stringResource(R.string.voicewake_keyword_applying)
                        } else {
                            listeningSubtitle(state.voiceState)
                        },
                        onClick = {
                            onAction(
                                VoiceWakeSettingsAction.ToggleListening(
                                    !state.voiceState.isRunning && !state.isStartPending,
                                ),
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = state.voiceState.isRunning || state.isStartPending,
                                onCheckedChange = {
                                    onAction(VoiceWakeSettingsAction.ToggleListening(it))
                                },
                            )
                        },
                    )
                }
            }
            if (!state.configurationReady) {
                item {
                    PreferenceBanner(
                        text = stringResource(R.string.voicewake_offline_setup_required),
                        tone = PreferenceBannerTone.Error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item {
                PreferenceSectionTitle(text = stringResource(R.string.voicewake_section_display))
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.PictureInPictureAlt,
                        title = stringResource(R.string.voicewake_overlay_permission_title),
                        subtitle = stringResource(
                            if (overlayPermissionGranted) {
                                R.string.voicewake_overlay_permission_granted
                            } else {
                                R.string.voicewake_overlay_permission_missing
                            },
                        ),
                        onClick = onOpenOverlaySettings,
                    )
                }
            }

            item {
                PreferenceSectionTitle(
                    text = stringResource(R.string.voicewake_section_models),
                )
                // 各模型描述只保留差异化的体积信息，下载与本地识别的共性说明收敛为标题下备注。
                Text(
                    text = stringResource(R.string.voicewake_section_models_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
                )
            }
            item {
                // 唤醒模型数量有限，整组渲染进同一张卡片。
                PreferenceGroupCard {
                    state.voiceState.availableModels.forEachIndexed { index, model ->
                        WakeModelRow(
                            model = model,
                            wakeWord = if (state.voiceState.activeModelId == model.id) {
                                state.voiceState.wakeWord
                            } else {
                                model.defaultWakeWord
                            },
                            modelState = state.voiceState.modelStates[model.id] ?: WakeModelState(),
                            isActive = state.voiceState.activeModelId == model.id,
                            showDivider = index < state.voiceState.availableModels.lastIndex,
                            onSelect = { onAction(VoiceWakeSettingsAction.SelectModel(model.id)) },
                            onInstall = { onAction(VoiceWakeSettingsAction.InstallModel(model.id)) },
                            onCancel = { onAction(VoiceWakeSettingsAction.CancelInstall(model.id)) },
                            onRemove = { onAction(VoiceWakeSettingsAction.RemoveModel(model.id)) },
                        )
                    }
                }
            }

            item {
                PreferenceSectionTitle(
                    text = stringResource(
                        R.string.voicewake_section_keyword_with_language,
                        stringResource(languageNameRes(state.voiceState.activeModel)),
                    ),
                )
            }
            item {
                WakeKeywordEditor(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun WakeKeywordEditor(
    state: VoiceWakeSettingsUiState,
    onAction: (VoiceWakeSettingsAction) -> Unit,
) {
    val model = state.voiceState.activeModel
    val isEnglish = model.languageTag.startsWith("en")
    var fieldValue by remember(model.id) {
        mutableStateOf(TextFieldValue(state.keywordDraft, TextRange(0, state.keywordDraft.length)))
    }
    var hadFocus by remember(model.id) { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.keywordDraft) {
        if (fieldValue.text != state.keywordDraft) {
            fieldValue = TextFieldValue(state.keywordDraft, TextRange(state.keywordDraft.length))
        }
    }
    val preview = state.keywordDraft.trim().ifEmpty { stringResource(R.string.voicewake_keyword_empty_preview) }
    val saveEnabled = state.hasUnsavedKeyword && state.keywordError == null
    PreferenceGroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 唤醒方式预览合并为一行“说出“X”然后说出你的任务”，不再单独展示描述标题。
            Text(
                text = stringResource(R.string.voicewake_keyword_say_preview, preview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { value ->
                    fieldValue = value
                    onAction(VoiceWakeSettingsAction.KeywordChanged(value.text))
                },
                label = { Text(stringResource(R.string.voicewake_keyword_field_label)) },
                singleLine = true,
                isError = state.keywordError != null,
                supportingText = {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.keywordError?.let { stringResource(keywordErrorRes(it, isEnglish)) }
                                ?: stringResource(
                                    if (isEnglish) R.string.voicewake_keyword_hint_en
                                    else R.string.voicewake_keyword_hint_zh,
                                ),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(
                            text = keywordCounter(state.keywordDraft, isEnglish),
                            color = if (isKeywordNearLimit(state.keywordDraft, isEnglish)) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                trailingIcon = if (fieldValue.text.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                fieldValue = TextFieldValue("")
                                onAction(VoiceWakeSettingsAction.KeywordChanged(""))
                            },
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.voicewake_keyword_clear),
                            )
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (saveEnabled) {
                            onAction(VoiceWakeSettingsAction.SaveKeyword)
                            keyboardController?.hide()
                        }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !hadFocus) {
                            fieldValue = fieldValue.copy(selection = TextRange(0, fieldValue.text.length))
                        }
                        hadFocus = focusState.isFocused
                    },
            )
            Text(
                text = stringResource(R.string.voicewake_keyword_recommendations),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                model.recommendedWakeWords.forEach { wakeWord ->
                    AssistChip(
                        onClick = {
                            onAction(VoiceWakeSettingsAction.SuggestedKeywordSelected(wakeWord))
                        },
                        label = { Text(wakeWord) },
                    )
                }
            }
            Text(
                text = stringResource(
                    if (state.voiceState.model.status == WakeModelStatus.Ready) {
                        R.string.voicewake_keyword_vocabulary_notice
                    } else {
                        R.string.voicewake_keyword_model_required
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.keywordSaveFailed) {
                Text(
                    text = stringResource(R.string.voicewake_keyword_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onAction(VoiceWakeSettingsAction.UseDefaultKeyword) },
                    enabled = normalizeWakeKeyword(state.keywordDraft) != model.defaultWakeWord,
                ) {
                    Text(stringResource(R.string.voicewake_keyword_use_default))
                }
                Button(
                    onClick = {
                        onAction(VoiceWakeSettingsAction.SaveKeyword)
                        keyboardController?.hide()
                    },
                    enabled = saveEnabled,
                ) {
                    Text(
                        stringResource(
                            if (state.keywordSaveFailed) {
                                R.string.voicewake_keyword_retry
                            } else {
                                R.string.voicewake_keyword_save
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsavedKeywordDialog(onAction: (VoiceWakeSettingsAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(VoiceWakeSettingsAction.DismissUnsavedChanges) },
        title = { Text(stringResource(R.string.voicewake_unsaved_title)) },
        text = { Text(stringResource(R.string.voicewake_unsaved_message)) },
        confirmButton = {
            TextButton(onClick = { onAction(VoiceWakeSettingsAction.SaveChangesAndLeave) }) {
                Text(stringResource(R.string.voicewake_unsaved_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onAction(VoiceWakeSettingsAction.DiscardChangesAndLeave) }) {
                    Text(stringResource(R.string.voicewake_unsaved_discard))
                }
                TextButton(onClick = { onAction(VoiceWakeSettingsAction.DismissUnsavedChanges) }) {
                    Text(stringResource(R.string.voicewake_unsaved_continue))
                }
            }
        },
    )
}

private fun keywordCounter(value: String, isEnglish: Boolean): String {
    val normalized = normalizeWakeKeyword(value)
    return if (isEnglish) {
        val words = normalized.takeIf(String::isNotEmpty)?.split(' ')?.size ?: 0
        "$words/4 · ${normalized.length}/40"
    } else {
        "${normalized.length}/20"
    }
}

private fun isKeywordNearLimit(value: String, isEnglish: Boolean): Boolean {
    val normalized = normalizeWakeKeyword(value)
    if (!isEnglish) return normalized.length >= 18
    val words = normalized.takeIf(String::isNotEmpty)?.split(' ')?.size ?: 0
    return words >= 4 || normalized.length >= 36
}

private fun keywordErrorRes(error: WakeKeywordError, isEnglish: Boolean): Int = when (error) {
    WakeKeywordError.InvalidLength -> if (isEnglish) {
        R.string.voicewake_keyword_error_length_en
    } else {
        R.string.voicewake_keyword_error_length_zh
    }
    WakeKeywordError.InvalidCharacters -> R.string.voicewake_keyword_error_characters
    WakeKeywordError.InvalidWordFormat -> R.string.voicewake_keyword_error_format_en
}

/** 运行中展示服务层实时状态（含唤醒词与设备名），停止时展示功能说明。 */
@Composable
private fun listeningSubtitle(voiceState: VoiceWakeState): String {
    val liveStatus = voiceState.message?.takeIf { voiceState.isRunning }
    if (liveStatus != null) {
        return listOfNotNull(liveStatus, voiceState.deviceName).joinToString(" · ")
    }
    return stringResource(R.string.voicewake_listening_subtitle)
}

@Composable
private fun WakeModelRow(
    model: WakeModelInfo,
    wakeWord: String,
    modelState: WakeModelState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
        headlineContent = {
            Text(
                stringResource(modelNameRes(model.id)),
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            when (modelState.status) {
                WakeModelStatus.Missing -> when (val source = model.source) {
                    is WakeModelSource.Bundled ->
                        Text(stringResource(R.string.voicewake_model_status_bundled))
                    is WakeModelSource.Downloadable ->
                        Text(
                            stringResource(
                                R.string.voicewake_model_status_download_required,
                                source.sizeBytes / 1_000_000,
                            ),
                        )
                }
                WakeModelStatus.Downloading -> Column {
                    val progressPercent = (modelState.progress * 100).toInt()
                    Text(
                        stringResource(
                            R.string.voicewake_model_downloading_progress,
                            progressPercent,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = { modelState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                WakeModelStatus.Extracting -> Text(stringResource(R.string.voicewake_model_installing))
                WakeModelStatus.Removing -> Text(stringResource(R.string.voicewake_model_removing))
                WakeModelStatus.Ready -> Text(
                    stringResource(
                        R.string.voicewake_model_ready,
                        wakeWord,
                    ),
                )
                WakeModelStatus.Error -> Text(
                    modelState.message ?: stringResource(R.string.voicewake_model_install_failed),
                )
            }
        },
        leadingContent = {
            RadioButton(selected = isActive, onClick = null)
        },
        trailingContent = {
            if (modelState.status == WakeModelStatus.Missing) {
                TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.voicewake_action_download))
                }
            } else if (modelState.status == WakeModelStatus.Error) {
                TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.voicewake_action_retry))
                }
            } else if (modelState.status == WakeModelStatus.Downloading ||
                modelState.status == WakeModelStatus.Extracting
            ) {
                TextButton(onClick = onCancel) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.voicewake_action_cancel))
                }
            } else if (modelState.status == WakeModelStatus.Ready) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.voicewake_action_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isActive, onClick = onSelect, role = Role.RadioButton),
    )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

private fun modelNameRes(modelId: String): Int = when (modelId) {
    WakeModelCatalog.English.id -> R.string.voicewake_model_en_name
    else -> R.string.voicewake_model_cn_name
}

private fun languageNameRes(model: WakeModelInfo): Int =
    if (model.languageTag.startsWith("en")) R.string.voicewake_language_en
    else R.string.voicewake_language_zh
