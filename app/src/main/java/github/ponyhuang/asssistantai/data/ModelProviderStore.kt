package github.ponyhuang.asssistantai.data

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型服务仓库。
 *
 * - 完整服务配置以 Android Keystore 加密后持久化，进程重启后会自动恢复。
 * - 首次升级时会把旧版单独缓存的远端模型迁移到完整配置快照。
 * - 所有写操作都走 `update { ... }`，订阅 `services` 的 UI 会自动重组。
 */
@Singleton
class ModelServiceRepository @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
) {

    private val _services = MutableStateFlow<List<ModelProvider>>(emptyList())
    private val gson = Gson()
    private val preferences = applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )
    private val _currentSelection = MutableStateFlow<ModelSelection?>(null)
    private val remoteModelsType = object : TypeToken<Map<String, List<ModelItem>>>() {}.type
    private val servicesType = object : TypeToken<List<ModelProvider>>() {}.type

    /** 当前所有服务列表。订阅本属性可获得变更通知。 */
    val services: StateFlow<List<ModelProvider>> = _services
    /** 当前激活会话的运行时模型选择；会话持久化由 ConversationMetadataDatabase 管理。 */
    val currentSelection: StateFlow<ModelSelection?> = _currentSelection.asStateFlow()

    init {
        val savedServices = readPersistedServices()
        if (savedServices != null) {
            _services.value = savedServices
        } else {
            _services.value = DefaultModelServices.services
            readLegacyRemoteModels().forEach { (serviceId, models) ->
                upsertModelsInternal(serviceId, models)
            }
            persistServices()
            preferences.edit(commit = true) { remove(remoteModelsKey) }
        }
    }

    /** 全量替换服务列表。仅供测试或受控的数据恢复流程使用。 */
    fun replaceAll(list: List<ModelProvider>) {
        _services.value = list
        persistServices()
    }

    /** 若当前为空则注入 [list]；否则保持现状。常用于列表页"空态自动种子"。 */
    fun seedIfEmpty(list: List<ModelProvider>) {
        if (_services.value.isEmpty()) {
            _services.value = list
            persistServices()
        }
    }

    /** 查单个服务；找不到返回 `null`。 */
    fun getService(serviceId: String): ModelProvider? =
        _services.value.firstOrNull { it.serviceId == serviceId }

    /**
     * 对单个服务做不可变替换。
     *
     * @param serviceId 目标服务 ID。
     * @param transform 把当前服务映射成下一个值；用 `it.copy(...)` 风格。
     */
    fun updateService(serviceId: String, transform: (ModelProvider) -> ModelProvider) {
        _services.update { list ->
            list.map { if (it.serviceId == serviceId) transform(it) else it }
        }
        persistServices()
    }

    /**
     * 设置服务的总开关（[ModelProvider.isEnabled]）。
     *
     * 列表页和详情页的 Switch 共用此入口，避免散落的 `updateService { copy(isEnabled = ...) }`。
     */
    fun setEnabled(serviceId: String, enabled: Boolean) {
        updateService(serviceId) { it.copy(isEnabled = enabled) }
    }

    /**
     * 从指定组中移除一个模型；找不到 group / model 时为 no-op。
     */
    fun removeModel(serviceId: String, groupId: String, modelId: String) {
        updateService(serviceId) { svc ->
            svc.copy(
                modelGroups = svc.modelGroups.map { group ->
                    if (group.groupId == groupId) {
                        group.copy(models = group.models.filterNot { it.modelId == modelId })
                    } else {
                        group
                    }
                }
            )
        }
    }

    /**
     * 在指定服务的**第一个组**末尾追加一个模型；若该服务没有组，则创建一个默认组。
     */
    fun appendModel(serviceId: String, model: ModelItem) {
        updateService(serviceId) { svc ->
            if (svc.modelGroups.isEmpty()) {
                svc.copy(
                    modelGroups = listOf(
                        ModelGroup(
                            groupId = "${svc.serviceId}-default",
                            groupName = "${svc.serviceName} 默认组",
                            models = listOf(model),
                        )
                    )
                )
            } else {
                svc.copy(
                    modelGroups = svc.modelGroups.mapIndexed { idx, group ->
                        if (idx == 0) group.copy(models = group.models + model) else group
                    }
                )
            }
        }
    }

    /**
     * 合并远端模型列表到默认组（第一个组）。以 [ModelItem.modelId] 为 key：
     * - 已存在 → 覆盖更新
     * - 不存在 → 追加
     * - 不会产生重复
     */
    fun upsertModels(serviceId: String, models: List<ModelItem>) {
        upsertModelsInternal(serviceId, models)
        persistServices()
    }

    private fun upsertModelsInternal(serviceId: String, models: List<ModelItem>) {
        updateService(serviceId) { svc ->
            if (svc.modelGroups.isEmpty()) {
                svc.copy(
                    modelGroups = listOf(
                        ModelGroup(
                            groupId = "${svc.serviceId}-default",
                            groupName = "${svc.serviceName} 默认组",
                            models = models,
                        )
                    )
                )
            } else {
                svc.copy(
                    modelGroups = svc.modelGroups.mapIndexed { idx, group ->
                        if (idx != 0) {
                            group
                        } else {
                            val merged = group.models.toMutableList()
                            for (m in models) {
                                val existIdx = merged.indexOfFirst { it.modelId == m.modelId }
                                if (existIdx >= 0) {
                                    merged[existIdx] = m
                                } else {
                                    merged.add(m)
                                }
                            }
                            group.copy(models = merged)
                        }
                    }
                )
            }
        }
    }

    private fun readLegacyRemoteModels(): Map<String, List<ModelItem>> {
        val raw = preferences.getString(remoteModelsKey, null)
            ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, List<ModelItem>>>(raw, remoteModelsType)
                .orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun readPersistedServices(): List<ModelProvider>? {
        val encrypted = preferences.getString(servicesKey, null) ?: return null
        return runCatching {
            val json = decrypt(encrypted)
            gson.fromJson<List<ModelProvider>>(json, servicesType)
                ?.takeIf { it.isNotEmpty() }
        }.onFailure { error ->
            Log.w(TAG, "Unable to restore model service configuration; using defaults.", error)
        }.getOrNull()
    }

    private fun persistServices() {
        runCatching {
            val encrypted = encrypt(gson.toJson(_services.value))
            preferences.edit(commit = true) { putString(servicesKey, encrypted) }
        }.onFailure { error ->
            Log.w(TAG, "Unable to persist model service configuration.", error)
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
        require(parts.size == 2) { "Invalid encrypted model service payload." }
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
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }
            .generateKey()
    }

    /** 按服务与模型的存储顺序返回首个可用于新会话的模型。 */
    fun defaultSelection(): ModelSelection? {
        return _services.value.asSequence()
            .filter { it.isEnabled }
            .mapNotNull { service ->
                service.modelGroups.firstOrNull { it.models.isNotEmpty() }?.let { group ->
                    ModelSelection(
                        serviceId = service.serviceId,
                        groupId = group.groupId,
                        modelId = group.models.first().modelId,
                    )
                }
            }
            .firstOrNull()
    }

    // ── 用户显式选择的"当前模型" ────────────────────────────────────────
    //
    // 这是当前激活会话的运行时镜像，而非持久化源。会话级持久化由
    // ConversationMetadataDatabase 负责，ChatViewModel 在打开会话时把配置装载到这里，
    // 供 AgentFactory.selectModelConfig 创建 runner 使用。
    //
    // 设计要点：
    // - getter 不做任何回退，始终反映用户的显式意图。回退是 AgentFactory 的事。
    // - [resolveSelection] 也不修改 `_currentSelection`：用户禁用其选中的服务后保留意图，
    //   重新启用服务后无需再次选择即可恢复。

    /** 设置或清空用户显式选择的当前模型。传 `null` 回到回退逻辑。 */
    fun setCurrentSelection(selection: ModelSelection?) {
        _currentSelection.value = selection
    }

    /**
     * 把 [selection] 解析到真实可达的 `(provider, group, model)`。
     *
     * 返回 `null` 的情形：
     * - [selection] 为 `null`；
     * - 指向的服务不存在；
     * - 服务被禁用；
     * - 指向的组 / 模型已不存在（被删除或重命名）。
     *
     * 不修改 `_currentSelection`：用户禁用其选中的服务后保留意图，重新启用后无需再次选择。
     */
    fun resolveSelection(selection: ModelSelection?): ResolvedModel? {
        if (selection == null) return null
        val svc = _services.value.firstOrNull { it.serviceId == selection.serviceId } ?: return null
        if (!svc.isEnabled) return null
        val group = svc.modelGroups.firstOrNull { it.groupId == selection.groupId } ?: return null
        val model = group.models.firstOrNull { it.modelId == selection.modelId } ?: return null
        return ResolvedModel(provider = svc, group = group, model = model)
    }

    /** [resolveSelection] 的解析结果。 */
    data class ResolvedModel(
        val provider: ModelProvider,
        val group: ModelGroup,
        val model: ModelItem,
    )

    private companion object {
        const val TAG = "ModelServiceRepository"
        const val preferencesName = "model_service_prefs"
        const val servicesKey = "encrypted_services"
        const val remoteModelsKey = "remote_models"
        const val keyAlias = "model_service_config_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
