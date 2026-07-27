package github.ponyhuang.asssistantai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.asssistantai.data.modelcatalog.toData
import github.ponyhuang.asssistantai.data.modelcatalog.toDomain
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.repository.ModelCatalogRepository
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ModelCatalogLoadState {
    data object Loading : ModelCatalogLoadState
    data object Ready : ModelCatalogLoadState
    data class Failed(val cause: Throwable) : ModelCatalogLoadState
}

/** Sensitive, user-editable provider settings stored outside the public Room catalog. */
private data class ModelServiceSettings(
    val isEnabled: Boolean,
    val apiKey: String,
    val apiBaseUrl: String,
    val baseType: ApiBaseType,
    val anthropicBaseUrl: String,
    /** Null keeps settings written before official-tool selection backward compatible. */
    val disabledOfficialTools: Set<String>? = null,
)

/**
 * Combines the Room-owned public model catalog with Keystore-encrypted connection settings.
 * Room is the only runtime source for providers, groups, and models.
 */
@Singleton
class ModelServiceRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val database: ModelServiceDatabase,
) : ModelCatalogRepository {
    private val gson = Gson()
    private val dao = database.modelServiceDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogWriteMutex = Mutex()
    private val settingsMutationLock = Any()
    private val ready = CompletableDeferred<Unit>()
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val settingsType = object : TypeToken<Map<String, ModelServiceSettings>>() {}.type
    private val groupsType = object : TypeToken<List<StoredModelGroup>>() {}.type
    private val selectionType = object : TypeToken<LLMModelSelection>() {}.type
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
    private val _currentSelection = MutableStateFlow<LLMModelSelection?>(null)
    private val _defaultAssistantSelection = MutableStateFlow(readSelection(DEFAULT_ASSISTANT_MODEL_KEY))
    private val _fastModelSelection = MutableStateFlow(readSelection(FAST_MODEL_KEY))
    private val _defaultSpeechSelection = MutableStateFlow(readSelection(DEFAULT_SPEECH_MODEL_KEY))
    private val _defaultTtsSelection = MutableStateFlow(readSelection(DEFAULT_TTS_MODEL_KEY))
    private val _defaultTtsVoice = MutableStateFlow(
        preferences.getString(DEFAULT_TTS_VOICE_KEY, null) ?: DEFAULT_TTS_VOICE,
    )

    val services: StateFlow<List<LLMModelProvider>> = _services.asStateFlow()
    val loadState: StateFlow<ModelCatalogLoadState> = _loadState.asStateFlow()
    val configurationRevision: StateFlow<Long> = _configurationRevision.asStateFlow()
    val currentSelection: StateFlow<LLMModelSelection?> = _currentSelection.asStateFlow()
    /** User-configured model used to initialize new assistant conversations. */
    val defaultAssistantSelection: StateFlow<LLMModelSelection?> = _defaultAssistantSelection.asStateFlow()
    /** User-configured low-latency model for features that require a quick response. */
    val fastModelSelection: StateFlow<LLMModelSelection?> = _fastModelSelection.asStateFlow()
    /** Explicitly configured speech-to-text model; there is intentionally no implicit fallback. */
    val defaultSpeechSelection: StateFlow<LLMModelSelection?> = _defaultSpeechSelection.asStateFlow()
    /** Explicitly configured text-to-speech model; there is intentionally no implicit fallback. */
    val defaultTtsSelection: StateFlow<LLMModelSelection?> = _defaultTtsSelection.asStateFlow()
    val defaultTtsVoice: StateFlow<String> = _defaultTtsVoice.asStateFlow()

    init {
        scope.launch {
            cancellationAwareRunCatching {
                settings.value = readInitialSettings()
                seedCatalogIfEmpty()
                dao.observeAll().collectLatest { entities ->
                    _services.value = entities.mapNotNull { entity ->
                        cancellationAwareRunCatching { entityToProvider(entity) }
                            .onFailure { error ->
                                Log.w(TAG, "Skipping corrupt model service ${entity.serviceId}.", error)
                            }
                            .getOrNull()
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
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeFastSelection() = fastModelSelection
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeSpeechSelection() = defaultSpeechSelection
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeTtsSelection() = defaultTtsSelection
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeTtsVoice() = defaultTtsVoice

    override fun currentService(serviceId: String): LLMModelSetting? = getService(serviceId)?.toDomain()

    override fun currentServices(): List<LLMModelSetting> = services.value.map { it.toDomain() }

    override fun currentAssistantSelection(): ModelSelection? =
        defaultAssistantSelection.value?.toDomain()

    override fun currentSpeechSelection(): ModelSelection? = defaultSpeechSelection.value?.toDomain()

    override fun currentTtsSelection(): ModelSelection? = defaultTtsSelection.value?.toDomain()

    override fun currentTtsVoice(): String = defaultTtsVoice.value

    override fun setRuntimeSelection(selection: ModelSelection?) {
        setCurrentSelection(selection?.toData())
    }

    override fun updateApiKey(serviceId: String, value: String) {
        updateService(serviceId) { it.copy(apiKey = value) }
    }

    override fun updateEnabled(serviceId: String, enabled: Boolean): Boolean =
        setEnabled(serviceId, enabled)

    override fun updateApiProtocol(serviceId: String, protocol: ApiProtocol) {
        updateService(serviceId) { provider ->
            val target = protocol.toData()
            // 单协议厂商（OpenAI / Anthropic）拒绝切换到不支持的接口标准。
            if (target in provider.supportedBaseTypes) provider.copy(baseType = target) else provider
        }
    }

    override fun updateBaseUrl(serviceId: String, value: String) {
        updateService(serviceId) { provider ->
            when (provider.baseType) {
                ApiBaseType.Standard -> provider.copy(apiBaseUrl = value)
                ApiBaseType.Anthropic -> provider.copy(anthropicBaseUrl = value)
            }
        }
    }

    override fun updateOfficialToolEnabled(
        serviceId: String,
        toolId: String,
        enabled: Boolean,
    ) {
        updateService(serviceId) { provider ->
            if (toolId !in provider.supportedOfficialTools) return@updateService provider
            provider.copy(
                disabledOfficialTools = if (enabled) {
                    provider.disabledOfficialTools - toolId
                } else {
                    provider.disabledOfficialTools + toolId
                },
            )
        }
    }

    override suspend fun addModel(serviceId: String, model: Model) {
        appendModel(serviceId, model.toData())
    }

    override suspend fun removeCatalogModel(serviceId: String, groupId: String, modelId: String) {
        removeModel(serviceId, groupId, modelId)
    }

    override suspend fun replaceRemoteModels(serviceId: String, models: List<Model>) {
        syncRemoteModels(serviceId, models.map(Model::toData))
    }

    override fun selectAssistantModel(selection: ModelSelection) {
        setDefaultAssistantSelection(selection.toData())
    }

    override fun selectFastModel(selection: ModelSelection) {
        setFastModelSelection(selection.toData())
    }

    override fun selectSpeechModel(selection: ModelSelection) {
        setDefaultSpeechSelection(selection.toData())
    }

    override fun selectTtsModel(selection: ModelSelection) {
        setDefaultTtsSelection(selection.toData())
    }

    override fun selectTtsVoice(voiceId: String) {
        setDefaultTtsVoice(voiceId)
    }

    fun getService(serviceId: String): LLMModelProvider? =
        _services.value.firstOrNull { it.serviceId == serviceId }

    /** Updates encrypted connection settings; public catalog fields are intentionally ignored. */
    fun updateService(serviceId: String, transform: (LLMModelProvider) -> LLMModelProvider) {
        synchronized(settingsMutationLock) {
            val current = getService(serviceId) ?: return
            val updated = transform(current).toSettings()
            val nextSettings = settings.value + (serviceId to updated)
            if (!persistSettings(nextSettings)) return
            settings.value = nextSettings
            refreshMergedServices()
            _configurationRevision.update { it + 1 }
        }
    }

    /**
     * 切换总开关。返回是否真的发生了状态变化：
     * - `enabled=true` 但 apiKey 为空 → 拒绝并返回 `false`（保护用户，避免无密钥启用后调用失败）。
     * - 其他情况正常切换并返回 `true`。
     */
    fun setEnabled(serviceId: String, enabled: Boolean): Boolean {
        val current = getService(serviceId) ?: return false
        if (enabled && current.apiKey.isBlank()) {
            Log.w(TAG, "Refusing to enable '$serviceId' — apiKey is blank.")
            return false
        }
        updateService(serviceId) { it.copy(isEnabled = enabled) }
        return true
    }

    suspend fun removeModel(serviceId: String, groupId: String, modelId: String) {
        mutateCatalog(serviceId) { groups ->
            removeStoredModel(groups, groupId, modelId)
        }
    }

    suspend fun appendModel(serviceId: String, model: LLMModelItem) {
        mutateCatalog(serviceId) { groups ->
            appendUserModel(
                groups = groups,
                serviceId = serviceId,
                serviceName = getService(serviceId)?.serviceName ?: serviceId,
                model = model,
            )
        }
    }

    /** Replaces the remote catalog snapshot while preserving manually entered models. */
    suspend fun syncRemoteModels(serviceId: String, models: List<LLMModelItem>) {
        mutateCatalog(serviceId) { existingGroups ->
            syncStoredRemoteModels(
                existingGroups = existingGroups,
                serviceId = serviceId,
                serviceName = getService(serviceId)?.serviceName ?: serviceId,
                models = models,
            )
        }
    }

    /**
     * Resolves the default assistant model, falling back to the first usable model when the
     * saved selection is absent or has since been disabled/removed.
     */
    fun defaultSelection(): LLMModelSelection? =
        _defaultAssistantSelection.value?.takeIf { resolveChatSelection(it) != null }
            ?: firstAvailableSelection()

    private fun firstAvailableSelection(): LLMModelSelection? = _services.value.asSequence()
        .filter { it.isConfiguredForChat }
        .mapNotNull { service ->
            service.LLMModelGroups.asSequence().mapNotNull { group ->
                group.models.firstOrNull { it.isChatModel }?.let { model ->
                    LLMModelSelection(service.serviceId, group.groupId, model.modelId)
                }
            }.firstOrNull()
        }
        .firstOrNull()

    fun setDefaultAssistantSelection(selection: LLMModelSelection?) {
        _defaultAssistantSelection.value = selection
        persistSelection(DEFAULT_ASSISTANT_MODEL_KEY, selection)
    }

    fun setFastModelSelection(selection: LLMModelSelection?) {
        _fastModelSelection.value = selection
        persistSelection(FAST_MODEL_KEY, selection)
    }

    fun setDefaultSpeechSelection(selection: LLMModelSelection?) {
        _defaultSpeechSelection.value = selection
        persistSelection(DEFAULT_SPEECH_MODEL_KEY, selection)
    }

    fun setDefaultTtsSelection(selection: LLMModelSelection?) {
        _defaultTtsSelection.value = selection
        persistSelection(DEFAULT_TTS_MODEL_KEY, selection)
    }

    fun setDefaultTtsVoice(voiceId: String) {
        val normalized = voiceId.trim().ifEmpty { DEFAULT_TTS_VOICE }
        _defaultTtsVoice.value = normalized
        preferences.edit { putString(DEFAULT_TTS_VOICE_KEY, normalized) }
    }

    fun setCurrentSelection(selection: LLMModelSelection?) {
        _currentSelection.value = selection
    }

    /** Resolves a normal chat model and rejects dedicated speech models. */
    fun resolveChatSelection(selection: LLMModelSelection?): ResolvedModel? =
        resolveRawSelection(selection)?.takeIf {
            it.provider.apiKey.isNotBlank() && it.model.isChatModel
        }

    /** Resolves a configured STT model only when its enabled provider has a usable API key. */
    fun resolveSpeechSelection(selection: LLMModelSelection?): ResolvedModel? =
        resolveRawSelection(selection)?.takeIf { it.model.isStt && it.provider.apiKey.isNotBlank() }

    /** Resolves a configured TTS model only when its enabled provider has a usable API key. */
    fun resolveTtsSelection(selection: LLMModelSelection?): ResolvedModel? =
        resolveRawSelection(selection)?.takeIf { it.model.isTts && it.provider.apiKey.isNotBlank() }

    private fun resolveRawSelection(selection: LLMModelSelection?): ResolvedModel? {
        if (selection == null) return null
        val provider = getService(selection.serviceId) ?: return null
        if (!provider.isEnabled) return null
        val group = provider.LLMModelGroups.firstOrNull { it.groupId == selection.groupId } ?: return null
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
        seedMissingModelCatalog(database, gson)
        // Merge newly built-in model groups / items into providers the user already
        // has, so adding a model to an existing DefaultModelServices.services entry in
        // an app update surfaces in the running app's settings without clearing the
        // user's selections. Both helpers are idempotent and only write when the
        // snapshot actually changes.
        upgradeDefaultModelMetadata(database, gson)
    }

    private suspend fun mutateCatalog(
        serviceId: String,
        transform: (List<StoredModelGroup>) -> List<StoredModelGroup>,
    ) {
        awaitReady()
        catalogWriteMutex.withLock {
            database.withTransaction {
                val entity = dao.get(serviceId) ?: return@withTransaction
                val groups = decodeGroups(entity.modelGroupsJson)
                dao.upsert(entity.copy(modelGroupsJson = gson.toJson(transform(groups))))
            }
        }
    }

    private fun refreshMergedServices() {
        _services.value = _services.value.map { provider ->
            provider.applySettings(settings.value[provider.serviceId] ?: provider.toSettings())
        }
    }

    private fun entityToProvider(entity: ModelServiceEntity): LLMModelProvider {
        val providerSettings = settings.value[entity.serviceId]
            ?: defaultSettings[entity.serviceId]
            ?: ModelServiceSettings(false, "", "", ApiBaseType.Standard, "")
        // 接口标准白名单是静态元数据，按 serviceId 从默认清单回填；
        // 持久化的协议若不在白名单内（例如厂商后来收紧了格式约束），回退到首个允许值。
        val supportedBaseTypes = LLMModelConfigs.supportedBaseTypesFor(entity.serviceId)
        val baseType = providerSettings.baseType.takeIf { it in supportedBaseTypes }
            ?: supportedBaseTypes.first()
        return LLMModelProvider(
            serviceId = entity.serviceId,
            serviceName = entity.serviceName,
            isEnabled = providerSettings.isEnabled,
            apiKey = providerSettings.apiKey,
            apiBaseUrl = providerSettings.apiBaseUrl,
            baseType = baseType,
            supportedBaseTypes = supportedBaseTypes,
            anthropicBaseUrl = providerSettings.anthropicBaseUrl,
            LLMModelGroups = decodeGroups(entity.modelGroupsJson).map { group ->
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
                        )
                    },
                )
            },
            // 品牌图标是静态元数据，按 serviceId 从默认清单回填，
            // 新增厂商只需在 DefaultModelServices 配 iconRes，UI 自动生效。
            iconRes = LLMModelConfigs.iconFor(entity.serviceId),
            homepageUrl = entity.homepageUrl,
            keyHelpUrl = entity.keyHelpUrl,
            docsUrl = entity.docsUrl,
            modelsUrl = entity.modelsUrl,
            officialToolProtocols = LLMModelConfigs.officialToolProtocolsFor(entity.serviceId),
            disabledOfficialTools = providerSettings.disabledOfficialTools.orEmpty(),
        )
    }

    private fun decodeGroups(json: String): List<StoredModelGroup> =
        gson.fromJson<List<StoredModelGroup>>(json, groupsType).orEmpty()

    private fun LLMModelProvider.toSettings(): ModelServiceSettings = ModelServiceSettings(
        isEnabled = isEnabled,
        apiKey = apiKey,
        apiBaseUrl = apiBaseUrl,
        baseType = baseType,
        anthropicBaseUrl = anthropicBaseUrl,
        disabledOfficialTools = disabledOfficialTools,
    )

    private fun LLMModelProvider.applySettings(value: ModelServiceSettings): LLMModelProvider = copy(
        isEnabled = value.isEnabled,
        apiKey = value.apiKey,
        apiBaseUrl = value.apiBaseUrl,
        baseType = value.baseType,
        anthropicBaseUrl = value.anthropicBaseUrl,
        disabledOfficialTools = value.disabledOfficialTools.orEmpty(),
    )

    private fun readSettings(): Map<String, ModelServiceSettings>? {
        val encrypted = preferences.getString(SETTINGS_KEY, null) ?: return null
        return runCatching {
            gson.fromJson<Map<String, ModelServiceSettings>>(decrypt(encrypted), settingsType)
        }.onFailure { Log.w(TAG, "Unable to restore model service settings.", it) }.getOrNull()
    }

    private fun readInitialSettings(): Map<String, ModelServiceSettings> {
        return readSettings() ?: defaultSettings.also {
            check(persistSettings(defaultSettings)) {
                "Unable to persist initial model service settings."
            }
        }
    }

    private fun persistSettings(value: Map<String, ModelServiceSettings>): Boolean =
        cancellationAwareRunCatching {
            preferences.edit()
                .putString(SETTINGS_KEY, encrypt(gson.toJson(value)))
                .commit()
        }.onFailure { Log.w(TAG, "Unable to persist model service settings.", it) }
            .getOrDefault(false)

    private fun readSelection(key: String): LLMModelSelection? = preferences.getString(key, null)
        ?.let { encoded -> runCatching { gson.fromJson<LLMModelSelection>(encoded, selectionType) }.getOrNull() }

    private fun persistSelection(key: String, selection: LLMModelSelection?) {
        preferences.edit {
            if (selection == null) remove(key) else putString(key, gson.toJson(selection))
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":", limit = 2)
        require(parts.size == 2) { "Invalid encrypted model service settings." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            .toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
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
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
