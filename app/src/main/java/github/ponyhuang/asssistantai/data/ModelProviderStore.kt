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
)

/**
 * Combines the Room-owned public model catalog with Keystore-encrypted connection settings.
 * Room is the only runtime source for providers, groups, and models.
 */
@Singleton
class ModelServiceRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val database: ModelServiceDatabase,
) {
    private val gson = Gson()
    private val dao = database.modelServiceDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val catalogWriteMutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val settingsType = object : TypeToken<Map<String, ModelServiceSettings>>() {}.type
    private val groupsType = object : TypeToken<List<StoredModelGroup>>() {}.type
    private val defaultSettings = DefaultModelServices.services.associate { provider ->
        provider.serviceId to provider.toSettings()
    }
    private val settings = MutableStateFlow(readSettings() ?: defaultSettings.also(::persistSettings))
    private val _services = MutableStateFlow<List<ModelProvider>>(emptyList())
    private val _loadState = MutableStateFlow<ModelCatalogLoadState>(ModelCatalogLoadState.Loading)
    private val _currentSelection = MutableStateFlow<ModelSelection?>(null)

    val services: StateFlow<List<ModelProvider>> = _services.asStateFlow()
    val loadState: StateFlow<ModelCatalogLoadState> = _loadState.asStateFlow()
    val currentSelection: StateFlow<ModelSelection?> = _currentSelection.asStateFlow()

    init {
        scope.launch {
            runCatching {
                seedCatalogIfEmpty()
                dao.observeAll().collectLatest { entities ->
                    _services.value = entities.map(::entityToProvider)
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

    suspend fun awaitReady() {
        ready.await()
    }

    fun getService(serviceId: String): ModelProvider? =
        _services.value.firstOrNull { it.serviceId == serviceId }

    /** Updates encrypted connection settings; public catalog fields are intentionally ignored. */
    fun updateService(serviceId: String, transform: (ModelProvider) -> ModelProvider) {
        val current = getService(serviceId) ?: return
        val updated = transform(current).toSettings()
        settings.value = settings.value + (serviceId to updated)
        persistSettings(settings.value)
        refreshMergedServices()
    }

    fun setEnabled(serviceId: String, enabled: Boolean) {
        updateService(serviceId) { it.copy(isEnabled = enabled) }
    }

    suspend fun removeModel(serviceId: String, groupId: String, modelId: String) {
        mutateCatalog(serviceId) { groups ->
            removeStoredModel(groups, groupId, modelId)
        }
    }

    suspend fun appendModel(serviceId: String, model: ModelItem) {
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
    suspend fun syncRemoteModels(serviceId: String, models: List<ModelItem>) {
        mutateCatalog(serviceId) { existingGroups ->
            syncStoredRemoteModels(
                existingGroups = existingGroups,
                serviceId = serviceId,
                serviceName = getService(serviceId)?.serviceName ?: serviceId,
                models = models,
            )
        }
    }

    fun defaultSelection(): ModelSelection? = _services.value.asSequence()
        .filter { it.isEnabled }
        .mapNotNull { service ->
            service.modelGroups.firstOrNull { it.models.isNotEmpty() }?.let { group ->
                ModelSelection(service.serviceId, group.groupId, group.models.first().modelId)
            }
        }
        .firstOrNull()

    fun setCurrentSelection(selection: ModelSelection?) {
        _currentSelection.value = selection
    }

    fun resolveSelection(selection: ModelSelection?): ResolvedModel? {
        if (selection == null) return null
        val provider = getService(selection.serviceId) ?: return null
        if (!provider.isEnabled) return null
        val group = provider.modelGroups.firstOrNull { it.groupId == selection.groupId } ?: return null
        val model = group.models.firstOrNull { it.modelId == selection.modelId } ?: return null
        return ResolvedModel(provider, group, model)
    }

    data class ResolvedModel(
        val provider: ModelProvider,
        val group: ModelGroup,
        val model: ModelItem,
    )

    private suspend fun seedCatalogIfEmpty() {
        seedModelCatalogIfEmpty(database, gson)
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

    private fun entityToProvider(entity: ModelServiceEntity): ModelProvider {
        val providerSettings = settings.value[entity.serviceId]
            ?: defaultSettings[entity.serviceId]
            ?: ModelServiceSettings(false, "", "", ApiBaseType.Standard, "")
        return ModelProvider(
            serviceId = entity.serviceId,
            serviceName = entity.serviceName,
            isEnabled = providerSettings.isEnabled,
            apiKey = providerSettings.apiKey,
            apiBaseUrl = providerSettings.apiBaseUrl,
            baseType = providerSettings.baseType,
            anthropicBaseUrl = providerSettings.anthropicBaseUrl,
            modelGroups = decodeGroups(entity.modelGroupsJson).map { group ->
                ModelGroup(
                    groupId = group.groupId,
                    groupName = group.groupName,
                    isExpanded = group.isExpanded,
                    models = group.models.map { ModelItem(it.modelId, it.modelName) },
                )
            },
            homepageUrl = entity.homepageUrl,
            keyHelpUrl = entity.keyHelpUrl,
            docsUrl = entity.docsUrl,
            modelsUrl = entity.modelsUrl,
        )
    }

    private fun decodeGroups(json: String): List<StoredModelGroup> =
        gson.fromJson<List<StoredModelGroup>>(json, groupsType).orEmpty()

    private fun ModelProvider.toSettings(): ModelServiceSettings = ModelServiceSettings(
        isEnabled = isEnabled,
        apiKey = apiKey,
        apiBaseUrl = apiBaseUrl,
        baseType = baseType,
        anthropicBaseUrl = anthropicBaseUrl,
    )

    private fun ModelProvider.applySettings(value: ModelServiceSettings): ModelProvider = copy(
        isEnabled = value.isEnabled,
        apiKey = value.apiKey,
        apiBaseUrl = value.apiBaseUrl,
        baseType = value.baseType,
        anthropicBaseUrl = value.anthropicBaseUrl,
    )

    private fun readSettings(): Map<String, ModelServiceSettings>? {
        val encrypted = preferences.getString(SETTINGS_KEY, null) ?: return null
        return runCatching {
            gson.fromJson<Map<String, ModelServiceSettings>>(decrypt(encrypted), settingsType)
        }.onFailure { Log.w(TAG, "Unable to restore model service settings.", it) }.getOrNull()
    }

    private fun persistSettings(value: Map<String, ModelServiceSettings>) {
        runCatching {
            preferences.edit(commit = true) {
                putString(SETTINGS_KEY, encrypt(gson.toJson(value)))
            }
        }.onFailure { Log.w(TAG, "Unable to persist model service settings.", it) }
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
        const val PREFERENCES_NAME = "model_service_settings_v1"
        const val SETTINGS_KEY = "encrypted_settings"
        const val KEY_ALIAS = "model_service_settings_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
