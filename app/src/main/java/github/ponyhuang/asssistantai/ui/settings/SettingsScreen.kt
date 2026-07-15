package github.ponyhuang.asssistantai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import github.ponyhuang.asssistantai.BuildConfig
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import github.ponyhuang.asssistantai.ui.chat.EnabledModelRow
import github.ponyhuang.asssistantai.ui.chat.ModelPickerDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import javax.inject.Inject

@HiltViewModel
class DefaultModelSettingsViewModel @Inject constructor(
    private val modelServices: ModelServiceRepository,
) : ViewModel() {
    val services = modelServices.services
    val defaultAssistantSelection = modelServices.defaultAssistantSelection
    val fastModelSelection = modelServices.fastModelSelection

    fun setDefaultAssistantModel(selection: LLMModelSelection) =
        modelServices.setDefaultAssistantSelection(selection)

    fun setFastModel(selection: LLMModelSelection) = modelServices.setFastModelSelection(selection)
}

/** Settings landing page containing only available settings capabilities. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModelService: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val services by viewModel.services.collectAsStateWithLifecycle()
    val defaultAssistantSelection by viewModel.defaultAssistantSelection.collectAsStateWithLifecycle()
    val fastModelSelection by viewModel.fastModelSelection.collectAsStateWithLifecycle()
    val enabledModels = remember(services) {
        services.filter { it.isEnabled }.flatMap { service ->
            service.LLMModelGroups.flatMap { group ->
                group.models.map { model -> EnabledModelRow(service, group, model) }
            }
        }
    }
    var selectingDefaultAssistant by remember { mutableStateOf(false) }
    var selectingFastModel by remember { mutableStateOf(false) }

    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            item {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 28.dp),
                )
            }
            item {
                SettingsNavigationCard(
                    icon = Icons.Default.Tune,
                    title = "模型服务",
                    subtitle = "配置 API 密钥、地址和模型列表",
                    onClick = onNavigateToModelService,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsSectionTitle(
                    text = "默认模型",
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 8.dp),
                )
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    DefaultModelOption(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        title = "默认助手模型",
                        subtitle = "创建新助手时使用的模型；未设置时使用第一个可用模型",
                        selection = defaultAssistantSelection,
                        rows = enabledModels,
                        onClick = { selectingDefaultAssistant = true },
                    )
                    DefaultModelOption(
                        icon = Icons.Default.FlashOn,
                        title = "快速模型",
                        subtitle = "用于需要快速响应的助手功能",
                        selection = fastModelSelection,
                        rows = enabledModels,
                        onClick = { selectingFastModel = true },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item {
                SettingsCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "版本",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 20.dp).weight(1f),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (selectingDefaultAssistant) {
        ModelPickerDialog(
            rows = enabledModels,
            currentSelection = defaultAssistantSelection,
            title = "选择默认助手模型",
            onPick = { row ->
                viewModel.setDefaultAssistantModel(row.toSelection())
                selectingDefaultAssistant = false
            },
            onDismiss = { selectingDefaultAssistant = false },
        )
    }
    if (selectingFastModel) {
        ModelPickerDialog(
            rows = enabledModels,
            currentSelection = fastModelSelection,
            title = "选择快速模型",
            onPick = { row ->
                viewModel.setFastModel(row.toSelection())
                selectingFastModel = false
            },
            onDismiss = { selectingFastModel = false },
        )
    }
}

@Composable
private fun DefaultModelOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selection: LLMModelSelection?,
    rows: List<EnabledModelRow>,
    onClick: () -> Unit,
) {
    val selected = remember(rows, selection) {
        rows.firstOrNull { it.toSelection() == selection }
    }
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(selected?.let { "${it.model.modelName} | ${it.service.serviceName}" } ?: subtitle)
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Default.Tune, contentDescription = "选择$title") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

private fun EnabledModelRow.toSelection() = LLMModelSelection(
    serviceId = service.serviceId,
    groupId = group.groupId,
    modelId = model.modelId,
)
