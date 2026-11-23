package github.ponyhuang.gimi.data.environment

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.core.storage.AppDirectoryResolver
import github.ponyhuang.gimi.domain.environment.*
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 配套运行组件的包名，同时是其 Provider authority 与签名权限前缀。 */
const val RUNTIME_PACKAGE = "github.ponyhuang.gimi.environment.runtime"

/** 只通过签名保护的 Provider 与独立 UID 通信，文件字节和凭据不进入 Agent 元数据。 */
@Singleton
class CompanionEnvironmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryResolver: AppDirectoryResolver,
) : EnvironmentRepository {
    private fun extractBundledApk(): java.io.File {
        // 必须落在 FileProvider 已声明的 shareable/ 缓存目录，否则 getUriForFile 解析失败。
        val dir = directoryResolver.resolve(EnvironmentStorage.CompanionApk, create = true)
        val file = java.io.File(dir, "environment-runtime.apk")
        context.assets.open("environment-runtime.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }
    /**
     * 每个进程只解压一次；失败结果也缓存，避免状态轮询反复读 assets 并刷日志。
     * 解压失败意味着当前安装包不完整（缺 assets/environment-runtime.apk），
     * 属于构建/安装缺陷而非运行时故障，不能中断状态查询与推荐刷新等旁路调用。
     */
    private val bundledApk: Result<java.io.File> by lazy {
        try {
            Result.success(extractBundledApk())
        } catch (error: Exception) {
            Log.w(TAG, "Bundled runtime APK is unavailable", error)
            Result.failure(error)
        }
    }
    /** 内置包不可读时返回 null：版本比较降级为“无内置版本”，环境状态仍可正常返回。 */
    private val bundledVersion: RuntimeVersion? by lazy {
        bundledApk.getOrNull()?.let { file ->
            try {
                val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
                    ?: throw IOException("Invalid bundled runtime")
                check(info.packageName == RUNTIME_PACKAGE)
                info.runtimeVersion()
            } catch (error: Exception) {
                Log.w(TAG, "Bundled runtime APK is unreadable", error)
                null
            }
        }
    }
    override suspend fun companionApk(): String = withContext(Dispatchers.IO) {
        // 安装/更新是用户显式动作：缓存被回收时重新解压，资源缺失时给出明确失败文案。
        val cached = bundledApk.getOrNull()
        val file = cached?.takeIf { it.isFile } ?: try {
            extractBundledApk()
        } catch (error: Exception) {
            throw IOException("Bundled runtime APK is unavailable", error)
        }
        file.absolutePath
    }

    private fun PackageInfo.runtimeVersion() = RuntimeVersion(longVersionCode, versionName.orEmpty(),
        applicationInfo?.metaData?.getInt("github.ponyhuang.gimi.environment.API_VERSION", 0) ?: 0)

    /**
     * 安装与启用分两步：先经签名保护的 Provider 预登记操作，再由主 App（前台）拉起
     * 配套 APK 的入口 Activity 启动前台服务。Activity 不消费 extra，Provider 侧自启
     * Activity 在 Android 10+ 也会被后台启动限制拦截，因此必须由调用方发起。
     */
    private suspend fun requestOperation(operation: String) {
        call("requestOperation", Bundle().apply { putString("operation", operation) })
        launchRuntime()
    }
    private fun launchRuntime() {
        val intent = Intent().setClassName(RUNTIME_PACKAGE, "$RUNTIME_PACKAGE.RuntimeActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    private suspend fun call(method: String, args: Bundle = Bundle()): Bundle = withContext(Dispatchers.IO) {
        // 停止和状态查询保留为恢复入口，其余调用必须符合当前主 App 的接口版本。
        if (method != "status" && method != "enable" && !status().runtimeUsable) throw IOException("Runtime update required")
        // 运行组件安装/替换瞬间 provider 会短暂失联：Unknown authority 转成可恢复的
        // IO 失败，交给上层轮询与操作错误态自愈，绝不能让异常逃出边界崩溃宿主。
        val result = try {
            context.contentResolver.call(Uri.parse("content://$RUNTIME_PACKAGE"), method, null, args)
        } catch (error: IllegalArgumentException) {
            null
        } ?: throw IOException("Runtime unavailable")
        if (result.getBoolean("failed")) throw IOException("Runtime operation failed")
        result
    }
    override suspend fun status(): EnvironmentStatus = withContext(Dispatchers.IO) {
        val bundled = bundledVersion
        val installed = try { context.packageManager.getPackageInfo(RUNTIME_PACKAGE, PackageManager.GET_META_DATA).runtimeVersion() }
            catch (_: PackageManager.NameNotFoundException) { return@withContext EnvironmentStatus(bundledRuntimeVersion = bundled) }
        val versionStatus = EnvironmentStatus(available = true, runtimeVersion = installed, bundledRuntimeVersion = bundled)
        val b = try { call("status") } catch (error: IOException) {
            return@withContext versionStatus.copy(connectionAvailable = false)
        } catch (error: SecurityException) {
            return@withContext versionStatus.copy(connectionAvailable = false)
        }
        versionStatus.copy(installed = b.getBoolean("installed"), enabled = b.getBoolean("enabled"),
            installing = b.getBoolean("installing"), progress = b.getInt("progress"), failed = b.getBoolean("installFailed"),
            backgroundRestricted = if (b.containsKey("backgroundRestricted")) b.getBoolean("backgroundRestricted") else null)
    }
    override suspend fun install() = withContext(Dispatchers.IO) { requestOperation("install") }
    override suspend fun setEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            if (enabled) requestOperation("enable") else call("enable", Bundle().apply { putBoolean("enabled", false) })
        }
    }
    override suspend fun variables(): Map<String, String> {
        val json = JSONObject(call("variables").getString("json") ?: "{}")
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
    override suspend fun saveVariables(values: Map<String, String>) {
        require(validEnvironmentVariables(values))
        call("saveVariables", Bundle().apply { putString("json", JSONObject(values).toString()) })
    }
    override suspend fun listDirectory(path: String): List<EnvironmentDirectoryEntry> {
        val entries = JSONArray(call("list", Bundle().apply { putString("path", path) }).getString("json") ?: "[]")
        return (0 until entries.length()).map { index -> entries.getJSONObject(index).let {
            EnvironmentDirectoryEntry(it.getString("name"), it.getString("path"), it.getBoolean("directory"))
        } }
    }
    private fun args(session: String, path: String = "") = Bundle().apply { putString("session", session); putString("path", path) }
    override suspend fun execute(session: String, command: String, timeoutMillis: Long): EnvironmentCommandResult =
        call("execute", args(session).apply { putString("command", command); putLong("timeout", timeoutMillis) }).commandResult()
    override suspend fun readFile(session: String, path: String): ByteArray = call("read", args(session, path)).getByteArray("bytes") ?: byteArrayOf()
    override suspend fun writeFile(session: String, path: String, content: ByteArray) { call("write", args(session, path).apply { putByteArray("bytes", content) }) }
    override suspend fun startJob(session: String, command: String, keepAlive: Boolean): String = call("startJob", args(session).apply { putString("command", command); putBoolean("keepAlive", keepAlive) }).getString("id") ?: throw IOException("Missing job")
    override suspend fun writeJobInput(session: String, id: String, chars: String) {
        call("writeJobInput", args(session).apply {
            putString("id", id)
            putString("chars", chars)
        })
    }
    override suspend fun queryJob(session: String, id: String): EnvironmentJobStatus =
        call("queryJob", args(session).apply { putString("id", id) }).let {
            val running = it.getBoolean("running")
            EnvironmentJobStatus(running, it.getString("stdout").orEmpty(), it.getString("stderr").orEmpty(),
                if (running || !it.containsKey("exitCode")) null else it.getInt("exitCode"), it.getBoolean("timedOut"))
        }
    override suspend fun stopJob(session: String, id: String) { call("stopJob", args(session).apply { putString("id", id) }) }
    private fun Bundle.commandResult() = EnvironmentCommandResult(getString("stdout").orEmpty(), getString("stderr").orEmpty(), getInt("exitCode"), getBoolean("timedOut"))

    private companion object {
        const val TAG: String = "CompanionEnvironment"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object EnvironmentModule {
    @Provides fun fileBridge(implementation: CompanionFileBridge): EnvironmentFileBridge = implementation

    @Provides @Singleton fun repository(implementation: CompanionEnvironmentRepository): EnvironmentRepository = implementation
}
