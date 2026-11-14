package github.ponyhuang.gimi.data.modelcatalog

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedAgentModel
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.resolveConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.usecase.toConnection
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

/**
 * Combines the Room-owned public template catalog with one Keystore-encrypted
 * instance configuration snapshot.
 *
 * Room owns vendor templates and built-in candidate catalogs; a single encrypted
 * commit owns every user instance — connections, credentials, selected models —
 * plus the persisted default assignments kept unencrypted in the same preference
 * file. Candidate refreshes never publish selections.
 */
@Singleton
class ModelServiceConfigurationStore @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val database: ModelTemplateDatabase,
) : ModelCatalogRepository, AgentModelConfigurationSource {
    private val dao = database.modelTemplateDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsMutationLock = Any()
    private val ready = CompletableDeferred<Unit>()
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val cipher = KeystoreAesGcmStringCipher(KEY_ALIAS)

    // Keep construction cheap: Android Keystore can take noticeable time to create its
    // first key on a fresh install. The repository is only considered ready after the
    // persisted configuration has been restored on the IO scope below.
    private val _instances = MutableStateFlow<List<ModelServiceInstance>>(emptyList())
    private val _templates = MutableStateFlow<List<ModelServiceTemplate>>(emptyList())
    private val _loadState = MutableStateFlow<ModelCatalogLoadState>(ModelCatalogLoadState.Loading)
    private val _configurationRevision = MutableStateFlow(0L)
    private val _currentSelection = MutableStateFlow<ModelSelection?>(null)
    private val _defaultAssistantSelection = MutableStateFlow(readSelection(DEFAULT_ASSISTANT_MODEL_KEY))
    private val _fastModelSelection = MutableStateFlow(readSelection(FAST_MODEL_KEY))
    private val _defaultSpeechSelection = MutableStateFlow(readSelection(DEFAULT_SPEECH_MODEL_KEY))
    private val _defaultTtsSelection = MutableStateFlow(readSelection(DEFAULT_TTS_MODEL_KEY))
    private val _defaultTtsVoice = MutableStateFlow(readStoredTtsVoice())
    private val _defaultRealtimeSelection = MutableStateFlow(readSelection(DEFAULT_REALTIME_MODEL_KEY))
    private val _defaultRealtimeVoice = MutableStateFlow(readRealtimeVoiceOverride())

    val loadState: StateFlow<ModelCatalogLoadState> = _loadState.asStateFlow()
    val templates: StateFlow<List<ModelServiceTemplate>> = _templates.asStateFlow()
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
    /** Explicitly configured Realtime model; null means the Realtime mode is unavailable. */
    val defaultRealtimeSelection: StateFlow<ModelSelection?> = _defaultRealtimeSelection.asStateFlow()
    /** Custom Realtime session voice; null means the selected model's vendor default voice. */
    val defaultRealtimeVoice: StateFlow<RealtimeVoiceOverride?> = _defaultRealtimeVoice.asStateFlow()

    override val runtimeSelection: StateFlow<ModelSelection?> get() = currentSelection
    override val fastSelection: StateFlow<ModelSelection?> get() = fastModelSelection

    init {
        scope.launch {
            cancellationAwareRunCatching {
                // 启动对账：模板新增的能力连接补进已建实例（只增不改），变化时先落盘再发布。
                val stored = readInitialConfiguration()
                val reconciled = reconcileTemplateConnections(stored)
                // 落盘失败按快照原样发布，不打断初始化，下次启动重试对账。
                _instances.value =
                    if (reconciled != stored && commitConfiguration(reconciled) != null) reconciled else stored
                seedTemplates()
                dao.observeAll().collectLatest { entities ->
                    _templates.value = entities.mapNotNull { entity ->
                        templateFromEntity(entity)
                    }
                    if (!ready.isCompleted) {
                        _loadState.value = ModelCatalogLoadState.Ready
                        ready.complete(Unit)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to initialize model service configuration.", error)
                _loadState.value = ModelCatalogLoadState.Failed(error)
                if (!ready.isCompleted) ready.completeExceptionally(error)
            }
        }
    }

    override suspend fun awaitReady() {
        ready.await()
    }

    override fun observeLoadState() = loadState
        .map { state ->
            when (state) {
                ModelCatalogLoadState.Loading -> CatalogLoadState.Loading
                ModelCatalogLoadState.Ready -> CatalogLoadState.Ready
                is ModelCatalogLoadState.Failed -> CatalogLoadState.Failed(state.cause)
            }
        }
        .distinctUntilChanged()

    override fun observeTemplates() = templates

    override fun template(templateId: String): ModelServiceTemplate? =
        _templates.value.firstOrNull { it.id == templateId } ?: ModelServiceTemplates.fromId(templateId)

    override fun observeInstances() = _instances
        .map { instances -> instances.sortedBy { it.name } }
        .distinctUntilChanged()

    override fun observeInstance(instanceId: String) = _instances
        .map { instances -> instances.firstOrNull { it.id == instanceId } }
        .distinctUntilChanged()

    override fun currentInstances(): List<ModelServiceInstance> = _instances.value

    override fun currentInstance(instanceId: String): ModelServiceInstance? =
        _instances.value.firstOrNull { it.id == instanceId }

    override fun observeAssistantSelection() = defaultAssistantSelection

    override fun observeFastSelection() = fastModelSelection

    override fun observeSpeechSelection() = defaultSpeechSelection

    override fun observeTtsSelection() = defaultTtsSelection

    override fun observeTtsVoice() = defaultTtsVoice

    override fun observeRealtimeSelection() = defaultRealtimeSelection

    override fun observeRealtimeVoice() = defaultRealtimeVoice

    override fun currentAssistantSelection(): ModelSelection? = defaultAssistantSelection.value

    override fun currentFastSelection(): ModelSelection? = fastModelSelection.value

    override fun currentSpeechSelection(): ModelSelection? = defaultSpeechSelection.value

    override fun currentTtsSelection(): ModelSelection? = defaultTtsSelection.value

    override fun currentTtsVoice(): String = defaultTtsVoice.value

    override fun currentRealtimeSelection(): ModelSelection? = defaultRealtimeSelection.value

    override fun currentRealtimeVoice(): RealtimeVoiceOverride? = defaultRealtimeVoice.value

    override fun setRuntimeSelection(selection: ModelSelection?) {
        _currentSelection.value = selection
    }

    override suspend fun saveNewInstance(draft: ModelServiceDraft): ModelServiceInstance? =
        withContext(Dispatchers.IO) {
            awaitReady()
            val template = draft.templateId?.let { template(it) }
            if (!draft.hasValidConfiguration(template)) return@withContext null
            val instance = ModelServiceInstance(
                id = newInstanceIdentifier(),
                name = draft.name.trim(),
                templateId = draft.templateId,
                isEnabled = true,
                isOfficialToolsEnabled = draft.isOfficialToolsEnabled,
                sharedApiKey = draft.sharedApiKey.trim(),
                connections = normalizedConnections(draft.connections, template),
            )
            synchronized(settingsMutationLock) {
                commitConfiguration(_instances.value + instance)
            } ?: return@withContext null
            instance
        }

    override suspend fun saveInstance(draft: ModelServiceDraft): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        val template = draft.templateId?.let { template(it) }
        if (!draft.hasValidConfiguration(template)) return@withContext false
        val instanceId = draft.instanceId ?: return@withContext false
        synchronized(settingsMutationLock) {
            val current = _instances.value.firstOrNull { it.id == instanceId } ?: return@synchronized false
            // 启停是独立提交的操作：保存草稿读取存储中的最新启用状态，防止覆盖即时启停。
            val updated = current.copy(
                name = draft.name.trim(),
                isOfficialToolsEnabled = draft.isOfficialToolsEnabled,
                sharedApiKey = draft.sharedApiKey.trim(),
                connections = normalizedConnections(draft.connections, template),
            )
            commitConfiguration(_instances.value.map { if (it.id == instanceId) updated else it }) != null
        }
    }

    override suspend fun setInstanceEnabled(instanceId: String, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            awaitReady()
            synchronized(settingsMutationLock) {
                val current = _instances.value.firstOrNull { it.id == instanceId } ?: return@synchronized false
                // 停用保留连接、模型与默认引用；启用前要求凭据可用，避免"看起来可用"的死配置。
                if (enabled && current.sharedApiKey.isBlank() && current.connections.all {
                        it.credential == CredentialSource.Shared ||
                            (it.credential as? CredentialSource.Independent)?.apiKey.isNullOrBlank()
                    }) {
                    return@synchronized false
                }
                commitConfiguration(
                    _instances.value.map { if (it.id == instanceId) current.copy(isEnabled = enabled) else it },
                ) != null
            }
        }

    override suspend fun deleteInstance(instanceId: String): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        synchronized(settingsMutationLock) {
            val current = _instances.value.firstOrNull { it.id == instanceId } ?: return@synchronized false
            // 删除在同一提交内清理受影响默认分配与绑定音色，保持引用一致。
            val nextInstances = _instances.value.filterNot { it.id == instanceId }
            val stored = configurationWithClearedReferences(nextInstances, instanceId)
            val committed = commitConfiguration(nextInstances, stored)
            if (committed != null) {
                applyStoredDefaults(stored)
            }
            committed != null
        }
    }

    override suspend fun deletionImpact(instanceId: String): DeletionImpact {
        awaitReady()
        val roles = mutableListOf<DefaultModelRole>()
        if (_defaultAssistantSelection.value?.instanceId == instanceId) roles += DefaultModelRole.Assistant
        if (_fastModelSelection.value?.instanceId == instanceId) roles += DefaultModelRole.Fast
        if (_defaultSpeechSelection.value?.instanceId == instanceId) roles += DefaultModelRole.SpeechRecognition
        if (_defaultTtsSelection.value?.instanceId == instanceId) roles += DefaultModelRole.SpeechSynthesis
        if (_defaultRealtimeSelection.value?.instanceId == instanceId) roles += DefaultModelRole.Realtime
        return DeletionImpact(
            roles = roles,
            hasTtsVoiceBinding = _defaultTtsSelection.value?.instanceId == instanceId,
            hasRealtimeVoiceBinding = _defaultRealtimeVoice.value?.selection?.instanceId == instanceId,
        )
    }

    override suspend fun modelCandidates(instanceId: String, connectionId: String): List<Model> =
        withContext(Dispatchers.IO) {
            awaitReady()
            val instance = currentInstance(instanceId) ?: return@withContext emptyList()
            val connection = instance.connection(connectionId) ?: return@withContext emptyList()
            val templateCatalog = instance.templateId
                ?.let { template(it) }
                ?.connection(connection.capability)
                ?.catalog
                .orEmpty()
            (templateCatalog + connection.models).distinctBy { it.id }
        }

    override fun resolveConnection(selection: ModelSelection?): ResolvedServiceConnection? =
        _instances.value.resolveConnection(selection)

    override fun resolveChatModel(selection: ModelSelection?): ResolvedAgentModel? =
        resolveConnection(selection)?.takeIf { resolved ->
            ModelCapability.LLM in resolved.model.capabilities
        }?.let { resolved ->
            ResolvedAgentModel(
                serviceId = resolved.instanceId,
                templateId = resolved.templateId,
                protocol = resolved.protocol,
                modelId = resolved.modelId,
                apiKey = resolved.apiKey,
                modelBaseUrl = resolved.endpoint.trimEnd('/'),
                supportsImages = resolved.model.multimodal.supportsImages,
            )
        }

    /**
     * Resolves the default assistant model, falling back to the first usable model when the
     * saved selection is absent or has since been disabled/removed.
     */
    override fun defaultSelection(): ModelSelection? =
        _defaultAssistantSelection.value?.takeIf { resolveChatModel(it) != null }
            ?: firstAvailableSelection()

    private fun firstAvailableSelection(): ModelSelection? = _instances.value.asSequence()
        .filter { it.isEnabled }
        .mapNotNull { instance ->
            instance.conversationConnection?.let { connection ->
                connection.models.firstOrNull { ModelCapability.LLM in it.capabilities }?.let { model ->
                    ModelSelection(instance.id, connection.id, model.id)
                }
            }
        }
        .firstOrNull()

    override fun selectAssistantModel(selection: ModelSelection) = setDefaultAssistantSelection(selection)

    override fun selectFastModel(selection: ModelSelection) = setFastModelSelection(selection)

    override fun selectSpeechModel(selection: ModelSelection) = setDefaultSpeechSelection(selection)

    override fun selectTtsModel(selection: ModelSelection) = setDefaultTtsSelection(selection)

    override fun selectTtsVoice(voiceId: String) = setDefaultTtsVoice(voiceId)

    override fun selectRealtimeModel(selection: ModelSelection?) = setDefaultRealtimeSelection(selection)

    override fun selectRealtimeVoice(override: RealtimeVoiceOverride?) = setDefaultRealtimeVoice(override)

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
        // 音色表按服务组织：合成模型切换后旧音色不再可靠（跨厂商直接报 voice 不存在），
        // 清空让运行期回落到新服务的第一个内置音色。
        setDefaultTtsVoice("")
    }

    /** 未配置音色以空串表示；生效音色由合成仓储在请求期回落到当前服务默认。 */
    fun setDefaultTtsVoice(voiceId: String) {
        val normalized = voiceId.trim()
        _defaultTtsVoice.value = normalized
        preferences.edit { putString(DEFAULT_TTS_VOICE_KEY, normalized) }
    }

    fun setDefaultRealtimeSelection(selection: ModelSelection?) {
        _defaultRealtimeSelection.value = selection
        persistSelection(DEFAULT_REALTIME_MODEL_KEY, selection)
    }

    fun setDefaultRealtimeVoice(override: RealtimeVoiceOverride?) {
        _defaultRealtimeVoice.value = override
        preferences.edit {
            if (override == null) {
                remove(DEFAULT_REALTIME_VOICE_KEY)
            } else {
                putString(DEFAULT_REALTIME_VOICE_KEY, modelCatalogJson.encodeToString(override))
            }
        }
    }

    fun setCurrentSelection(selection: ModelSelection?) {
        _currentSelection.value = selection
    }

    /**
     * 持久化成功后才一次性发布；不得把失败写入暴露给运行中的消费者。
     *
     * 返回提交后的完整配置（实例列表 + 清理后的默认引用），失败返回 null；
     * [instances] 为 null 时使用内存当前值（仅默认引用变化的提交）。
     */
    private fun commitConfiguration(
        instances: List<ModelServiceInstance>,
        storedDefaults: StoredDefaultSelections? = null,
    ): CommittedConfiguration? {
        val stored = StoredConfiguration(instances.map(::StoredInstance))
        if (!persistConfiguration(stored)) return null
        _instances.value = instances
        _configurationRevision.update { it + 1 }
        return CommittedConfiguration(instances, storedDefaults ?: currentStoredDefaults())
    }

    private data class CommittedConfiguration(
        val instances: List<ModelServiceInstance>,
        val defaults: StoredDefaultSelections,
    )

    private fun currentStoredDefaults() = StoredDefaultSelections(
        assistantSelection = _defaultAssistantSelection.value,
        fastSelection = _fastModelSelection.value,
        speechSelection = _defaultSpeechSelection.value,
        ttsSelection = _defaultTtsSelection.value,
        ttsVoiceId = _defaultTtsVoice.value,
        realtimeSelection = _defaultRealtimeSelection.value,
        realtimeVoice = _defaultRealtimeVoice.value,
    )

    /**
     * 删除实例后的一致性视图：清掉引用该实例的默认分配，音色覆盖绑定在该实例时一并清除，
     * 朗读音色在合成默认被清理时回落到应用默认值。
     */
    private fun configurationWithClearedReferences(
        remainingInstances: List<ModelServiceInstance>,
        removedInstanceId: String,
    ): StoredDefaultSelections {
        val remainingIds = remainingInstances.mapTo(HashSet()) { it.id }
        // 只保留仍指向存在实例的引用；指向被删实例的引用一律清理。
        fun keeps(selection: ModelSelection?): ModelSelection? =
            selection?.takeIf { it.instanceId in remainingIds }

        val assistant = keeps(_defaultAssistantSelection.value)
        val fast = keeps(_fastModelSelection.value)
        val speech = keeps(_defaultSpeechSelection.value)
        val tts = keeps(_defaultTtsSelection.value)
        val realtime = keeps(_defaultRealtimeSelection.value)
        val realtimeVoice = _defaultRealtimeVoice.value?.takeIf { it.selection.instanceId != removedInstanceId }
        // 朗读音色跟随合成默认模型；默认被清理时音色回到未配置，由运行期回落服务默认音色。
        val ttsVoiceCleared = _defaultTtsSelection.value?.instanceId == removedInstanceId
        val ttsVoiceId = if (ttsVoiceCleared) "" else _defaultTtsVoice.value
        return StoredDefaultSelections(
            assistantSelection = assistant,
            fastSelection = fast,
            speechSelection = speech,
            ttsSelection = tts,
            ttsVoiceId = ttsVoiceId,
            realtimeSelection = realtime,
            realtimeVoice = realtimeVoice,
        )
    }

    private fun persistSelection(key: String, selection: ModelSelection?) {
        preferences.edit {
            if (selection == null) remove(key) else putString(key, modelCatalogJson.encodeToString(selection))
        }
        // 默认引用是运行时可用性的一部分；配置版本随之推进，可用性观察者据此重算。
        _configurationRevision.update { it + 1 }
    }

    /** 发布并持久化清理后的默认引用；删除提交用它保证重启后引用保持一致。 */
    private fun applyStoredDefaults(stored: StoredDefaultSelections) {
        _defaultAssistantSelection.value = stored.assistantSelection
        persistSelection(DEFAULT_ASSISTANT_MODEL_KEY, stored.assistantSelection)
        _fastModelSelection.value = stored.fastSelection
        persistSelection(FAST_MODEL_KEY, stored.fastSelection)
        _defaultSpeechSelection.value = stored.speechSelection
        persistSelection(DEFAULT_SPEECH_MODEL_KEY, stored.speechSelection)
        _defaultTtsSelection.value = stored.ttsSelection
        persistSelection(DEFAULT_TTS_MODEL_KEY, stored.ttsSelection)
        _defaultRealtimeSelection.value = stored.realtimeSelection
        persistSelection(DEFAULT_REALTIME_MODEL_KEY, stored.realtimeSelection)
        setDefaultTtsVoice(stored.ttsVoiceId)
        setDefaultRealtimeVoice(stored.realtimeVoice)
    }

    /** 保存前归一化连接：厂商自有端点回贴模板默认值，协议回落到模板允许集合。 */
    private fun normalizedConnections(
        connections: List<ServiceConnection>,
        template: ModelServiceTemplate?,
    ): List<ServiceConnection> = connections.map { connection ->
        val templateConnection = template?.connection(connection.capability)
        val protocol = when {
            templateConnection == null -> connection.protocol
            templateConnection.supports(connection.protocol) -> connection.protocol
            else -> templateConnection.protocols.first()
        }
        val endpoint = if (templateConnection?.endpointPolicy == EndpointPolicy.VendorManaged) {
            templateConnection.defaultEndpoint(protocol)
        } else {
            connection.endpoint.trim().trimEnd('/')
        }
        connection.copy(
            protocol = protocol,
            endpoint = endpoint,
            credential = when (val source = connection.credential) {
                CredentialSource.Shared -> source
                is CredentialSource.Independent -> CredentialSource.Independent(source.apiKey.trim())
            },
            workspaceId = connection.workspaceId?.trim()?.ifEmpty { null },
            models = connection.models.distinctBy { it.id },
        )
    }

    private suspend fun seedTemplates() {
        seedMissingTemplates(database)
        removeRemovedTemplates(database)
        upgradeTemplateCatalogs(database)
    }

    /**
     * 启动对账：把内置模板新增的能力连接补进已建实例。
     *
     * 模板升级（[seedTemplates]）只写 Room 模板行，而实例连接存在加密快照里：不在加载时
     * 对齐，应用更新带来的新能力连接（如厂商新增语音端点）对已建实例永远不可见。
     * 只增不改——既有连接、已选模型、凭据与默认分配一律不动；新连接按模板首选项派生
     * （空模型、共享凭据），是否选用模型由用户在模型管理页决定。对账用内置清单而非
     * Room 行，不依赖模板装载时序；无变化不写盘，幂等。
     */
    private fun reconcileTemplateConnections(
        instances: List<ModelServiceInstance>,
    ): List<ModelServiceInstance> = instances.map { instance ->
        val template = instance.templateId?.let(ModelServiceTemplates::fromId) ?: return@map instance
        val missing = template.connections
            .filter { instance.connection(it.capability) == null }
            .map { it.toConnection() }
        if (missing.isEmpty()) instance else instance.copy(connections = instance.connections + missing)
    }

    private fun templateFromEntity(entity: ModelTemplateEntity): ModelServiceTemplate? {
        val connections = decodeTemplateConnections(entity.connectionsJson)
        if (connections.isEmpty()) return null
        return ModelServiceTemplate(
            id = entity.templateId,
            name = entity.displayName,
            connections = connections,
            homepageUrl = entity.homepageUrl,
            keyHelpUrl = entity.keyHelpUrl,
        )
    }

    private fun readConfiguration(): List<ModelServiceInstance>? {
        val encrypted = preferences.getString(CONFIGURATION_KEY, null) ?: return null
        val plainText = cipher.decryptHealing(encrypted) ?: return null
        return runCatching {
            modelCatalogJson.decodeFromString<StoredConfiguration>(plainText).instances.map { it.toDomain() }
        }.onFailure { Log.w(TAG, "Unable to restore model service configuration.", it) }.getOrNull()
    }

    private fun readInitialConfiguration(): List<ModelServiceInstance> {
        return readConfiguration() ?: emptyList<ModelServiceInstance>().also {
            check(persistConfiguration(StoredConfiguration(emptyList()))) {
                "Unable to persist initial model service configuration."
            }
        }
    }

    private fun persistConfiguration(value: StoredConfiguration): Boolean =
        cancellationAwareRunCatching {
            preferences.edit()
                .putString(CONFIGURATION_KEY, cipher.encryptOrThrow(modelCatalogJson.encodeToString(value)))
                .commit()
        }.onFailure { Log.w(TAG, "Unable to persist model service configuration.", it) }
            .getOrDefault(false)

    private fun readSelection(key: String): ModelSelection? = preferences.getString(key, null)
        ?.let { encoded ->
            runCatching { modelCatalogJson.decodeFromString<ModelSelection>(encoded) }.getOrNull()
        }

    /** 读取 Realtime 音色覆盖；损坏的持久化数据按未配置处理。 */
    private fun readRealtimeVoiceOverride(): RealtimeVoiceOverride? =
        preferences.getString(DEFAULT_REALTIME_VOICE_KEY, null)?.let { encoded ->
            runCatching { modelCatalogJson.decodeFromString<RealtimeVoiceOverride>(encoded) }.getOrNull()
        }

    /**
     * 读取朗读音色；未配置返回空串，生效音色由合成仓储回落到当前服务的第一个内置音色。
     * 旧版用 `mimo_default` 当「未配置」哨兵值，会把 MiMo 专属音色原样发给其他厂商
     * （MiniMax 报 2054 voice id not exist），读取时按未配置归一；
     * 下次存储版本升级（PREFERENCES_NAME v2）后此归一可移除。
     */
    private fun readStoredTtsVoice(): String =
        preferences.getString(DEFAULT_TTS_VOICE_KEY, null)?.trim()
            ?.takeUnless { it.isEmpty() || it == LEGACY_TTS_VOICE_SENTINEL } ?: ""

    private fun newInstanceIdentifier(): String = "svc-" + java.util.UUID.randomUUID()

    private companion object {
        const val TAG = "ModelServiceConfigurationStore"
        // v1：实例化配置的新存储；旧版 model_service_settings_v3 按服务标识保存、
        // 无法表达多实例与连接级凭据，开发期直接重建（见仓库重构约定）。
        const val PREFERENCES_NAME = "model_service_configuration_v1"
        const val CONFIGURATION_KEY = "encrypted_configuration"
        const val DEFAULT_ASSISTANT_MODEL_KEY = "default_assistant_model"
        const val FAST_MODEL_KEY = "fast_model"
        const val DEFAULT_SPEECH_MODEL_KEY = "default_speech_model"
        const val DEFAULT_TTS_MODEL_KEY = "default_tts_model"
        const val DEFAULT_TTS_VOICE_KEY = "default_tts_voice"

        /** 已废弃的「未配置」哨兵值；仅为兼容旧存储保留归一判断，存储版本升级后移除。 */
        const val LEGACY_TTS_VOICE_SENTINEL = "mimo_default"
        const val DEFAULT_REALTIME_MODEL_KEY = "default_realtime_model"
        const val DEFAULT_REALTIME_VOICE_KEY = "default_realtime_voice"
        const val KEY_ALIAS = "model_service_configuration_key"
    }
}

/** 目录初始化状态；对外以领域 [CatalogLoadState] 表达。 */
sealed interface ModelCatalogLoadState {
    data object Loading : ModelCatalogLoadState
    data object Ready : ModelCatalogLoadState
    data class Failed(val cause: Throwable) : ModelCatalogLoadState
}

/** 加密快照中的实例集合；单键整体提交保证原子性。 */
@Serializable
internal data class StoredConfiguration(
    val instances: List<StoredInstance> = emptyList(),
)

/** 持久化实例；连接凭据与其覆盖值一并保存。 */
@Serializable
internal data class StoredInstance(
    val id: String,
    val name: String,
    /** null 表示自定义 LLM 服务。 */
    val templateId: String? = null,
    val isEnabled: Boolean = false,
    val isOfficialToolsEnabled: Boolean = false,
    val sharedApiKey: String = "",
    val connections: List<StoredConnection> = emptyList(),
) {
    constructor(instance: ModelServiceInstance) : this(
        id = instance.id,
        name = instance.name,
        templateId = instance.templateId,
        isEnabled = instance.isEnabled,
        isOfficialToolsEnabled = instance.isOfficialToolsEnabled,
        sharedApiKey = instance.sharedApiKey,
        connections = instance.connections.map(::StoredConnection),
    )

    fun toDomain(): ModelServiceInstance = ModelServiceInstance(
        id = id,
        name = name,
        templateId = templateId,
        isEnabled = isEnabled,
        isOfficialToolsEnabled = isOfficialToolsEnabled,
        sharedApiKey = sharedApiKey,
        // 损坏的连接按缺失处理，不让单条坏数据拖垮整个实例。
        connections = connections.mapNotNull { it.toDomain() },
    )
}

/** 持久化连接：凭据来源以"是否共享 + 独立值"的平铺形态保存。 */
@Serializable
internal data class StoredConnection(
    val id: String,
    val capability: String,
    val protocol: String,
    val endpoint: String = "",
    val credentialShared: Boolean = true,
    /** 独立凭据值；共享连接忽略该字段。 */
    val apiKey: String = "",
    val workspaceId: String? = null,
    val models: List<StoredModel> = emptyList(),
) {
    constructor(connection: ServiceConnection) : this(
        id = connection.id,
        capability = connection.capability.name,
        protocol = connection.protocol.name,
        endpoint = connection.endpoint,
        credentialShared = connection.credential == CredentialSource.Shared,
        apiKey = (connection.credential as? CredentialSource.Independent)?.apiKey.orEmpty(),
        workspaceId = connection.workspaceId,
        models = connection.models.map(::StoredModel),
    )

    fun toDomain(): ServiceConnection? {
        val capability = ServiceCapability.entries.firstOrNull { it.name == capability } ?: return null
        val protocol = apiProtocolByName(protocol) ?: return null
        return ServiceConnection(
            id = id,
            capability = capability,
            protocol = protocol,
            endpoint = endpoint,
            credential = if (credentialShared) {
                CredentialSource.Shared
            } else {
                CredentialSource.Independent(apiKey)
            },
            workspaceId = workspaceId,
            models = models.map { it.toDomain() },
        )
    }
}

/** 加密快照之外随配置一起发布的默认引用视图；删除提交用它一次性清理。 */
internal data class StoredDefaultSelections(
    val assistantSelection: ModelSelection?,
    val fastSelection: ModelSelection?,
    val speechSelection: ModelSelection?,
    val ttsSelection: ModelSelection?,
    val ttsVoiceId: String,
    val realtimeSelection: ModelSelection?,
    val realtimeVoice: RealtimeVoiceOverride?,
)

/** 协议持久化名到枚举的解析；未知取值返回 null，由上层丢弃该连接。 */
internal fun apiProtocolByName(name: String): github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol? =
    github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.entries.firstOrNull { it.name == name }
