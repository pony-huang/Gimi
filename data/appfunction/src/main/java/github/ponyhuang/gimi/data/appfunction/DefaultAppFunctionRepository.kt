package github.ponyhuang.gimi.data.appfunction

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.appfunction.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 可替换的平台发现边界；不向领域层泄露 AndroidX 类型。 */
interface AppFunctionDiscovery {
    suspend fun discover(): AppFunctionCatalog
}

/** 仅保存用户明确开启的包名，新发现应用不自动获得使用权限。 */
interface AppFunctionPreferences {
    fun read(): Set<String>
    fun write(packages: Set<String>)
}

/** 应用级开关存储，与目标应用的系统级 enabled 状态分离。 */
@Singleton
class AndroidAppFunctionPreferences @Inject constructor(@ApplicationContext context: Context) : AppFunctionPreferences {
    private val preferences = context.getSharedPreferences("appfunction", Context.MODE_PRIVATE)
    override fun read(): Set<String> = preferences.getStringSet("enabled_packages", emptySet()).orEmpty().toSet()
    override fun write(packages: Set<String>) {
        check(preferences.edit().putStringSet("enabled_packages", packages).commit()) { "Could not save AppFunction settings" }
    }
}

/** 串行发布发现快照及开关；保存成功后才发布 revision，使缓存与实际配置一致。 */
@Singleton
class DefaultAppFunctionRepository @Inject constructor(
    private val discovery: AppFunctionDiscovery,
    private val preferences: AppFunctionPreferences,
) : AppFunctionRepository {
    private val mutableCatalog = MutableStateFlow(AppFunctionCatalog())
    private val mutableEnabled = MutableStateFlow(preferences.read())
    private val mutableRevision = MutableStateFlow(0L)
    private val mutex = Mutex()
    override val catalog = mutableCatalog.asStateFlow()
    override val enabledPackages = mutableEnabled.asStateFlow()
    override val revision = mutableRevision.asStateFlow()

    override suspend fun refresh() = mutex.withLock {
        val result = try {
            discovery.discover()
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            AppFunctionCatalog(AppFunctionAvailability.PERMISSION_REQUIRED)
        } catch (_: Exception) {
            // 平台/AppSearch 是可恢复边界，不将厂商异常内容暴露到 UI 或日志。
            AppFunctionCatalog(AppFunctionAvailability.FAILED)
        }
        val sorted = result.copy(apps = result.apps.sortedWith(compareBy({ it.name.lowercase(java.util.Locale.ROOT) }, { it.packageName })))
        if (mutableCatalog.value != sorted) {
            mutableCatalog.value = sorted
            mutableRevision.value += 1
        }
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) = mutex.withLock {
        val previous = mutableEnabled.value
        val next = if (enabled) previous + packageName else previous - packageName
        if (next != previous) {
            // commit 无法撤销，离开页面时也必须把已保存的状态和 revision 一起发布。
            withContext(NonCancellable + Dispatchers.IO) {
                preferences.write(next)
                mutableEnabled.value = next
                mutableRevision.value += 1
            }
        }
    }
}

/** AppFunction 能力的依赖绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppFunctionModule {
    @Binds abstract fun repository(implementation: DefaultAppFunctionRepository): AppFunctionRepository
    @Binds abstract fun discovery(implementation: AndroidAppFunctionDiscovery): AppFunctionDiscovery
    @Binds abstract fun preferences(implementation: AndroidAppFunctionPreferences): AppFunctionPreferences
}
