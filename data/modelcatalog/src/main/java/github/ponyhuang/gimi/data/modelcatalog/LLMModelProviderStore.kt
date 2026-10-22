package github.ponyhuang.gimi.data.modelcatalog

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.data.modelcatalog.toData
import github.ponyhuang.gimi.data.modelcatalog.toDomain
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

sealed interface ModelCatalogLoadState {
    data object Loading : ModelCatalogLoadState
    data object Ready : ModelCatalogLoadState
    data class Failed(val cause: Throwable) : ModelCatalogLoadState
}

/** Sensitive, user-editable provider settings stored outside the public Room catalog. */
@Serializable
internal data class ModelServiceSettings(
    // 补默认值容忍旧版本稀疏 blob（Gson 静默留 null / 缺字段，kotlinx 默认抛异常）。
    val isEnabled: Boolean = false,
    val isOfficialToolsEnabled: Boolean = isEnabled,
    /** 文件上传偏好独立于官方工具，只有已验证的聊天引用能力才可使用。 */
    val isFileUploadEnabled: Boolean = false,
    /** 仅影响新上传文件的有效期，支持 1 / 7 / 30 天。 */
    val fileUploadRetentionDays: Int = 7,
    val apiKey: String = "",
    val apiBaseUrl: String = "",
    val baseType: ApiBaseType = ApiBaseType.OpenaiChatcompletions,
    val anthropicBaseUrl: String = "",
    val disabledOfficialTools: Set<String> = emptySet(),
    val isAdded: Boolean = false,
    /** null 表示沿用已有目录；空列表表示用户显式选择零个模型。 */
    val selectedGroups: List<StoredModelGroup>? = null,
)

/**
 * Combines the Room-owned public model catalog with Keystore-encrypted connection settings.
 * Room owns provider templates and model candidates; one encrypted settings commit owns
 * each user configuration, including its selected models. Candidate refreshes never publish selections.
 */
@Singleton
class ModelServiceRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val database: LLMModelRoomDatabase,
) : ModelCatalogRepository, AgentModelConfigurationSource {
    private val dao = database.lLMModelConfigDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var catalogEntities: List<LLMModelConfigEntity> = emptyList()
    private val settingsMutationLock = Any()
    private val ready = CompletableDeferred<Unit>()
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)
    private val defaultSettings = LLMModelConfigs.services.associate { provider ->
        provider.serviceId to provider.toSettings()
    }
    // Keep construction cheap: Android Keystore can take noticeable time to create its
    // first key on a fresh install. The repository is only considered ready after the
    // persisted settings have been restored on the IO scope below.
    private val settings = MutableStateFlow(defaultSettings)
    private val _services = MutableStateFlow<List<LLMModelProvider>>(emptyList())
    private val _loadState = MutableStateFlow<ModelCatalogLoadState>(ModelCatalogLoadState.Loading)
    private val _configurationRevision = MutableStateFlow(0L)
    private val _currentSelection = MutableStateFlow<ModelSelection?>(null)
    private val _defaultAssistantSelection = MutableStateFlow(readSelection(DEFAULT_ASSISTANT_MODEL_KEY))
    private val _fastModelSelection = MutableStateFlow(readSelection(FAST_MODEL_KEY))
    private val _defaultSpeechSelection = MutableStateFlow(readSelection(DEFAULT_SPEECH_MODEL_KEY))
    private val _defaultTtsSelection = MutableStateFlow(readSelection(DEFAULT_TTS_MODEL_KEY))
    private val _defaultTtsVoice = MutableStateFlow(
        preferences.getString(DEFAULT_TTS_VOICE_KEY, null) ?: DEFAULT_TTS_VOICE,
    )

    val services: StateFlow<List<LLMModelProvider>> = _services.asStateFlow()
    val loadState: StateFlow<ModelCatalogLoadState> = _loadState.asStateFlow()
    override val configurationRevision: StateFlow<Long> = _configurationRevision.asStateFlow()
    val currentSelection: StateFlow<ModelSelection?> = _currentSelection.asStateFlow()
    /** User-configured model used to initialize new assistant conversations. */
    val defaultAssistantSelection: StateFlow<ModelSelection?> = _defaultAssistantSelection.asStateFlow()
    /** User-configured low-latency model for features that require a quick response. */
    val fastModelSelection: StateFlow<ModelSelection?> = _fastModelSelection.asStateFlow()
    /** Explicitly configured speech-to-text model; there is intentionally no implicit fallback. */
    val defaultSpeechSelection: StateFlow<ModelSelection?> = _defaultSpeechSelection.asStateFlow()
    /** Explicitly configured text-to-speech model; there is intentionally no implicit fallback. */
    val defaultTtsSelection: StateFlow<ModelSelection?> = _defaultTtsSelection.asStateFlow()
    val defaultTtsVoice: StateFlow<String> = _defaultTtsVoice.asStateFlow()

    override val runtimeSelection: StateFlow<ModelSelection?> get() = currentSelection
    override val fastSelection: StateFlow<ModelSelection?> get() = fastModelSelection

    init {
        scope.launch {
            cancellationAwareRunCatching {
                settings.value = readInitialSettings()
                seedCatalogIfEmpty()
                dao.observeAll().collectLatest { entities ->
                    synchronized(settingsMutationLock) {
                        catalogEntities = entities
                        _services.value = entities.filter { entity ->
                            LLMModelConfigs.isSupportedService(entity.serviceId)
                        }.mapNotNull { entity ->
                            cancellationAwareRunCatching { entityToProvider(entity) }
                                .onFailure { error ->
                                    Log.w(TAG, "Skipping corrupt model service ${entity.serviceId}.", error)
                                }
                                .getOrNull()
                        }
                    }
                    if (!ready.isCompleted) {
                        _loadState.value = ModelCatalogLoadState.Ready
                        ready.complete(Unit)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to initialize model catalog.", error)
                _loadState.value = ModelCatalogLoadState.Failed(error)
                if (!ready.isCompleted) ready.completeExceptionally(error)
            }
        }
    }

    override suspend fun awaitReady() {
        ready.await()
    }

    override fun observeService(serviceId: String) = services
        .map { providers -> providers.firstOrNull { it.serviceId == serviceId }?.toDomain() }
        .distinctUntilChanged()

    override fun observeServices() = services
        .map { providers -> providers.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeLoadState() = loadState
        .map { state ->
            when (state) {
                ModelCatalogLoadState.Loading -> CatalogLoadState.Loading
                ModelCatalogLoadState.Ready -> CatalogLoadState.Ready
                is ModelCatalogLoadState.Failed -> CatalogLoadState.Failed(state.cause)
            }
        }
        .distinctUntilChanged()

    override fun observeAssistantSelection() = defaultAssistantSelection

    override fun observeFastSelection() = fastModelSelection

    override fun observeSpeechSelection() = defaultSpeechSelection

    override fun observeTtsSelection() = defaultTtsSelection

    override fun observeTtsVoice() = defaultTtsVoice

    override fun currentService(serviceId: String): LLMModelSetting? = getService(serviceId)?.toDomain()

    override fun currentServices(): List<LLMModelSetting> = services.value.map { it.toDomain() }

    override fun currentAssistantSelection(): ModelSelection? = defaultAssistantSelection.value

    override fun currentSpeechSelection(): ModelSelection? = defaultSpeechSelection.value

    override fun currentTtsSelection(): ModelSelection? = defaultTtsSelection.value

    override fun currentTtsVoice(): String = defaultTtsVoice.value

    override fun setRuntimeSelection(selection: ModelSelection?) {
        setCurrentSelection(selection)
    }

    override suspend fun modelCandidates(serviceId: String): List<ModelGroup> = withContext(Dispatchers.IO) {
        awaitReady()
        val entity = dao.get(serviceId) ?: return@withContext emptyList()
        val template = entityToProvider(entity, useSelection = false).toDomain()
        val selected = currentService(serviceId)?.groups.orEmpty()
        (template.groups + selected).groupBy { it.id }.map { (_, groups) ->
            groups.first().copy(models = groups.flatMap { it.models }.distinctBy { it.id })
        }
    }

    override suspend fun saveService(service: LLMModelSetting): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        if (!service.hasValidConnectionConfiguration()) return@withContext false
        synchronized(settingsMutationLock) {
            val current = settings.value[service.id] ?: return@synchronized false
            val provider = getService(service.id) ?: return@synchronized false
            if (service.apiProtocol.toData() !in provider.supportedBaseTypes) return@synchronized false
            val updated = current.copy(
                isAdded = true,
                isEnabled = service.isEnabled,
                isOfficialToolsEnabled = service.isOfficialToolsEnabled,
                isFileUploadEnabled = service.isFileUploadEnabled,
                fileUploadRetentionDays = service.fileUploadRetentionDays,
                apiKey = service.apiKey.trim(),
                apiBaseUrl = service.apiBaseUrl.trim().trimEnd('/'),
                anthropicBaseUrl = service.anthropicBaseUrl.trim().trimEnd('/'),
                baseType = service.apiProtocol.toData(),
                selectedGroups = service.groups.map { group ->
                    StoredModelGroup(
                        groupId = group.id,
                        groupName = group.name,
                        models = group.models.distinctBy { it.id }.map { model ->
                            StoredModel(
                                modelId = model.id,
                                modelName = model.name,
                                isStt = model.isStt,
                                isTts = model.isTts,
                                capabilities = model.capabilities,
                            )
                        },
                    )
                },
            )
            commitSettings(service.id, updated)
        }
    }

    override suspend fun setServiceEnabled(serviceId: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        synchronized(settingsMutationLock) {
            val current = settings.value[serviceId] ?: return@synchronized false
            if (!(current.isAdded || current.apiKey.isNotBlank())) return@synchronized false
            if (enabled && current.apiKey.isBlank()) return@synchronized false
            // 暂停仅改变运行时可用性，不覆盖用户对厂商工具的选择。
            commitSettings(serviceId, current.copy(isEnabled = enabled))
        }
    }

    override suspend fun deleteService(serviceId: String): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        synchronized(settingsMutationLock) {
            val defaults = defaultSettings[serviceId] ?: return@synchronized false
            commitSettings(serviceId, defaults.copy(
                isAdded = false, isEnabled = false, isOfficialToolsEnabled = false,
                isFileUploadEnabled = false, fileUploadRetentionDays = 7,
                apiKey = "", selectedGroups = null,
            ))
        }
    }

    /** 持久化成功后才一次性发布；不得把草稿或失败写入暴露给运行中的消费者。 */
    private fun commitSettings(serviceId: String, updated: ModelServiceSettings): Boolean {
        val nextSettings = settings.value + (serviceId to updated)
        if (!persistSettings(nextSettings)) return false
        settings.value = nextSettings
        refreshMergedServices()
        _configurationRevision.update { it + 1 }
        return true
    }

    override fun selectAssistantModel(selection: ModelSelection) {
        setDefaultAssistantSelection(selection)
    }

    override fun selectFastModel(selection: ModelSelection) {
        setFastModelSelection(selection)
    }

    override fun selectSpeechModel(selection: ModelSelection) {
        setDefaultSpeechSelection(selection)
    }

    override fun selectTtsModel(selection: ModelSelection) {
        setDefaultTtsSelection(selection)
    }

    override fun selectTtsVoice(voiceId: String) {
        setDefaultTtsVoice(voiceId)
    }

    fun getService(serviceId: String): LLMModelProvider? =
        _services.value.firstOrNull { it.serviceId == serviceId }

    /**
     * Resolves the default assistant model, falling back to the first usable model when the
     * saved selection is absent or has since been disabled/removed.
     */
    override fun defaultSelection(): ModelSelection? =
        _defaultAssistantSelection.value?.takeIf { resolveChatSelection(it) != null }
            ?: firstAvailableSelection()

    private fun firstAvailableSelection(): ModelSelection? = _services.value.asSequence()
        .filter { it.isConfiguredForChat }
        .mapNotNull { service ->
            service.lLMModelGroups.asSequence().mapNotNull { group ->
                group.models.firstOrNull { it.isChatModel }?.let { model ->
                    ModelSelection(service.serviceId, group.groupId, model.modelId)
                }
            }.firstOrNull()
        }
        .firstOrNull()

    fun setDefaultAssistantSelection(selection: ModelSelection?) {
        _defaultAssistantSelection.value = selection
        persistSelection(DEFAULT_ASSISTANT_MODEL_KEY, selection)
    }

    fun setFastModelSelection(selection: ModelSelection?) {
        _fastModelSelection.value = selection
        persistSelection(FAST_MODEL_KEY, selection)
    }

    fun setDefaultSpeechSelection(selection: ModelSelection?) {
        _defaultSpeechSelection.value = selection
        persistSelection(DEFAULT_SPEECH_MODEL_KEY, selection)
    }

    fun setDefaultTtsSelection(selection: ModelSelection?) {
        _defaultTtsSelection.value = selection
        persistSelection(DEFAULT_TTS_MODEL_KEY, selection)
    }

    fun setDefaultTtsVoice(voiceId: String) {
        val normalized = voiceId.trim().ifEmpty { DEFAULT_TTS_VOICE }
        _defaultTtsVoice.value = normalized
        preferences.edit { putString(DEFAULT_TTS_VOICE_KEY, normalized) }
    }

    fun setCurrentSelection(selection: ModelSelection?) {
        _currentSelection.value = selection
    }

    override fun resolveChatModel(selection: ModelSelection?): ResolvedAgentModel? =
        resolveChatSelection(selection)?.let { resolved ->
            val provider = resolved.provider
            ResolvedAgentModel(
                serviceId = provider.serviceId,
                protocol = provider.baseType.toDomain(),
                modelId = resolved.model.modelId,
                apiKey = provider.apiKey,
                modelBaseUrl = provider.activeApiBaseUrl.trimEnd('/'),
            )
        }

    /** Resolves a normal chat model and rejects dedicated speech models. */
    fun resolveChatSelection(selection: ModelSelection?): ResolvedModel? =
        resolveRawSelection(selection)?.takeIf {
            it.provider.apiKey.isNotBlank() && it.model.isChatModel
        }

    /** Resolves a configured STT model only when its enabled provider has a usable API key. */
    fun resolveSpeechSelection(selection: ModelSelection?): ResolvedModel? =
        resolveRawSelection(selection)?.takeIf { it.model.isStt && it.provider.apiKey.isNotBlank() }

    /** Resolves a configured TTS model only when its enabled provider has a usable API key. */
    fun resolveTtsSelection(selection: ModelSelection?): ResolvedModel? =
        resolveRawSelection(selection)?.takeIf { it.model.isTts && it.provider.apiKey.isNotBlank() }

    private fun resolveRawSelection(selection: ModelSelection?): ResolvedModel? {
        if (selection == null) return null
        val provider = getService(selection.serviceId) ?: return null
        if (!provider.isEnabled) return null
        val group = provider.lLMModelGroups.firstOrNull { it.groupId == selection.groupId } ?: return null
        val model = group.models.firstOrNull { it.modelId == selection.modelId } ?: return null
        return ResolvedModel(provider, group, model)
    }

    data class ResolvedModel(
        val provider: LLMModelProvider,
        val group: LLMModelGroup,
        val model: LLMModelItem,
    )

    private suspend fun seedCatalogIfEmpty() {
        // Insert providers that are missing from the Room snapshot so newly built-in
        // vendors appear on a fresh install without losing prior user data.
        seedMissingModelCatalog(database)
        // Merge newly built-in model groups / items into providers the user already
        // has, so adding a model to an existing DefaultModelServices.services entry in
        // an app update surfaces in the running app's settings without clearing the
        // user's selections. Both helpers are idempotent and only write when the
        // snapshot actually changes.
        upgradeDefaultModelMetadata(database)
    }

    private fun refreshMergedServices() {
        _services.value = catalogEntities.filter { LLMModelConfigs.isSupportedService(it.serviceId) }
            .map { entityToProvider(it) }
    }

    private fun entityToProvider(entity: LLMModelConfigEntity, useSelection: Boolean = true): LLMModelProvider {
        val providerSettings = settings.value[entity.serviceId]
            ?: defaultSettings[entity.serviceId]
            ?: ModelServiceSettings(
                isEnabled = false,
                isOfficialToolsEnabled = false,
                apiKey = "",
                apiBaseUrl = "",
                baseType = ApiBaseType.OpenaiChatcompletions,
                anthropicBaseUrl = "",
            )
        // 接口标准白名单是静态元数据，按 serviceId 从默认清单回填；
        // 持久化的协议若不在白名单内（例如厂商后来收紧了格式约束），回退到首个允许值。
        val supportedBaseTypes = LLMModelConfigs.supportedBaseTypesFor(entity.serviceId)
        val baseType = providerSettings.baseType.takeIf { it in supportedBaseTypes }
            ?: supportedBaseTypes.first()
        return LLMModelProvider(
            serviceId = entity.serviceId,
            serviceName = entity.serviceName,
            isAdded = providerSettings.isAdded || providerSettings.apiKey.isNotBlank(),
            isEnabled = providerSettings.isEnabled,
            isOfficialToolsEnabled = providerSettings.isOfficialToolsEnabled,
            isFileUploadEnabled = providerSettings.isFileUploadEnabled,
            fileUploadRetentionDays = providerSettings.fileUploadRetentionDays,
            apiKey = providerSettings.apiKey,
            apiBaseUrl = providerSettings.apiBaseUrl,
            baseType = baseType,
            supportedBaseTypes = supportedBaseTypes,
            anthropicBaseUrl = providerSettings.anthropicBaseUrl,
            lLMModelGroups = (providerSettings.selectedGroups.takeIf { useSelection }
                ?: decodeGroups(entity.modelGroupsJson)).map { group ->
                LLMModelGroup(
                    groupId = group.groupId,
                    groupName = group.groupName,
                    isExpanded = group.isExpanded,
                    models = group.models.map {
                        LLMModelItem(
                            modelId = it.modelId,
                            modelName = it.modelName,
                            isStt = it.isStt,
                            isTts = it.isTts,
                            capabilities = it.capabilities,
                        )
                    },
                )
            },
            // 品牌图标是静态元数据，按 serviceId 从默认清单回填，
            // 新增厂商只需在 DefaultModelServices 配 iconRes，UI 自动生效。
            iconRes = LLMModelConfigs.iconFor(entity.serviceId),
            homepageUrl = entity.homepageUrl,
            keyHelpUrl = entity.keyHelpUrl,
        )
    }

    private fun decodeGroups(json: String): List<StoredModelGroup> =
        runCatching { modelCatalogJson.decodeFromString<List<StoredModelGroup>>(json) }
            .getOrDefault(emptyList())

    private fun LLMModelProvider.toSettings(): ModelServiceSettings = ModelServiceSettings(
        isEnabled = isEnabled,
        isOfficialToolsEnabled = isOfficialToolsEnabled,
        isFileUploadEnabled = isFileUploadEnabled,
        fileUploadRetentionDays = fileUploadRetentionDays,
        apiKey = apiKey,
        apiBaseUrl = apiBaseUrl,
        baseType = baseType,
        anthropicBaseUrl = anthropicBaseUrl,
        isAdded = apiKey.isNotBlank(),
    )

    private fun readSettings(): Map<String, ModelServiceSettings>? {
        val encrypted = preferences.getString(SETTINGS_KEY, null) ?: return null
        val plainText = cipher.decryptHealing(encrypted) ?: return null
        return runCatching {
            modelCatalogJson.decodeFromString<Map<String, ModelServiceSettings>>(plainText)
        }.onFailure { Log.w(TAG, "Unable to restore model service settings.", it) }.getOrNull()
    }

    private fun readInitialSettings(): Map<String, ModelServiceSettings> {
        return readSettings()?.let { defaultSettings + it } ?: defaultSettings.also {
            check(persistSettings(defaultSettings)) {
                "Unable to persist initial model service settings."
            }
        }
    }

    private fun persistSettings(value: Map<String, ModelServiceSettings>): Boolean =
        cancellationAwareRunCatching {
            preferences.edit()
                .putString(SETTINGS_KEY, cipher.encryptOrThrow(modelCatalogJson.encodeToString(value)))
                .commit()
        }.onFailure { Log.w(TAG, "Unable to persist model service settings.", it) }
            .getOrDefault(false)

    private fun readSelection(key: String): ModelSelection? = preferences.getString(key, null)
        ?.let { encoded ->
            runCatching { modelCatalogJson.decodeFromString<ModelSelection>(encoded) }.getOrNull()
        }

    private fun persistSelection(key: String, selection: ModelSelection?) {
        preferences.edit {
            if (selection == null) remove(key) else putString(key, modelCatalogJson.encodeToString(selection))
        }
    }

    private companion object {
        const val TAG = "ModelServiceRepository"
        const val PREFERENCES_NAME = "model_service_settings_v2"
        const val SETTINGS_KEY = "encrypted_settings"
        const val DEFAULT_ASSISTANT_MODEL_KEY = "default_assistant_model"
        const val FAST_MODEL_KEY = "fast_model"
        const val DEFAULT_SPEECH_MODEL_KEY = "default_speech_model"
        const val DEFAULT_TTS_MODEL_KEY = "default_tts_model"
        const val DEFAULT_TTS_VOICE_KEY = "default_tts_voice"
        const val DEFAULT_TTS_VOICE = "mimo_default"
        const val KEY_ALIAS = "model_service_settings_key_v2"
    }
}
