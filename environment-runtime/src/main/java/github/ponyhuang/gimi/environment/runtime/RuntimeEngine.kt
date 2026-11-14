package github.ponyhuang.gimi.environment.runtime

import android.net.ConnectivityManager
import android.content.Context
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.environment.validEnvironmentVariables
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/** 独立应用拥有 rootfs、加密变量、进程与任务；主 App 仅持有签名保护的调用接口。 */
internal class RuntimeEngine private constructor(private val context: Context) {
    private val root = File(context.filesDir, "rootfs")
    private val marker = File(context.filesDir, "installed")
    private val preferences = context.getSharedPreferences("environment", Context.MODE_PRIVATE)
    private val cipher = KeystoreAesGcmStringCipher("local_environment_variables")
    private val installing = AtomicBoolean(false)
    @Volatile private var enabled = false
    @Volatile private var progress = 0
    @Volatile private var failed = false
    @Volatile private var canceled = false
    @Volatile private var pendingOperation: String? = null
    private val pool = Executors.newCachedThreadPool()
    private val processes = ConcurrentHashMap<String, RuntimeProcessControl>()
    /** 会话所有权、执行 Future 与流式输出共同持有，停止时统一回收。 */
    private class Job(
        val session: String,
        val stdout: RuntimeJobOutput,
        val stderr: RuntimeJobOutput,
        val task: FutureTask<Bundle>,
    )
    private val jobs = ConcurrentHashMap<String, Job>()
    private val shellScript by lazy { context.assets.open("runtime-shell.sh").bufferedReader().use { it.readText() } }

    fun enable() { check(marker.isFile); enabled = true }

    /**
     * 登记一次性操作供 [RuntimeActivity] 取用；Activity 因 LAUNCHER 必须 exported，
     * 操作只能经签名保护的 Provider 预登记，避免不可信 extra 触发安装/启用。
     */
    fun requestOperation(operation: String) {
        require(operation == "install" || operation == "enable")
        if (operation == "enable") check(marker.isFile)
        pendingOperation = operation
    }
    fun takePendingOperation(): String? = pendingOperation.also { pendingOperation = null }
    fun disable() {
        enabled = false
        // 安装被用户取消时不算失败，UI 回到未安装态而不是错误态。
        if (installing.getAndSet(false)) canceled = true
        processes.values.forEach { control -> pool.execute { control.terminate() } }
        processes.clear()
        synchronized(jobs) {
            jobs.values.forEach { it.task.cancel(true) }
            jobs.clear()
        }
    }

    fun call(method: String, args: Bundle): Bundle {
        when (method) {
            "status" -> return Bundle().apply {
                putBoolean("backgroundRestricted", context.getSystemService(ActivityManager::class.java).isBackgroundRestricted)
                putBoolean("installed", marker.isFile); putBoolean("enabled", enabled)
                putBoolean("installing", installing.get()); putInt("progress", progress); putBoolean("installFailed", failed)
            }
            "enable" -> { disable(); context.stopService(Intent(context, RuntimeService::class.java)); return Bundle() }
            "variables" -> return Bundle().apply { putString("json", variables().toString()) }
            "saveVariables" -> {
                val json = JSONObject(requireNotNull(args.getString("json")))
                require(validEnvironmentVariables(json.keys().asSequence().associateWith { json.getString(it) }))
                if (!preferences.edit().putString("variables", cipher.encryptOrThrow(json.toString())).commit()) throw IOException("Save failed")
                return Bundle()
            }
            "list" -> {
                check(marker.isFile)
                val path = args.getString("path") ?: "/"
                val directory = contained(root, path)
                val entries = directory.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name }) ?: throw IOException("Directory unavailable")
                val json = JSONArray()
                entries.take(1000).forEach { file ->
                    // 目录树不遍历指向 rootfs 外部的链接。
                    if (file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
                        json.put(JSONObject().put("name", file.name).put("path", path.trimEnd('/') + "/" + file.name).put("directory", file.isDirectory))
                    }
                }
                return Bundle().apply { putString("json", json.toString()) }
            }
        }
        check(enabled && marker.isFile)
        val session = requireNotNull(args.getString("session"))
        val workspace = File(root, "home/workspace/" + digest(session.toByteArray())).apply { mkdirs() }
        return when (method) {
            "importFile", "prepareSkill" -> {
                val descriptor = requireNotNull(args.getParcelable<android.os.ParcelFileDescriptor>("descriptor"))
                val path = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                    val exchange = RuntimeFileExchange(workspace)
                    if (method == "prepareSkill") exchange.prepareSkill(input)
                    else exchange.importFile(requireNotNull(args.getString("path")), input)
                }
                Bundle().apply { putString("path", "/home/workspace/${digest(session.toByteArray())}/$path") }
            }
            "execute" -> {
                // binder call 不可取消：前台执行强制 [30s 默认, 10min 封顶]，防宿主永久 busy。
                val requested = args.getLong("timeout", 30_000)
                val timeout = when {
                    requested <= 0 -> 30_000L
                    requested > MAX_SYNC_TIMEOUT_MILLIS -> MAX_SYNC_TIMEOUT_MILLIS
                    else -> requested
                }
                execute(session, requireNotNull(args.getString("command")), timeout)
            }
            "read" -> {
                val file = contained(workspace, requireNotNull(args.getString("path")))
                require(file.length() <= 256 * 1024)
                Bundle().apply { putByteArray("bytes", file.readBytes()) }
            }
            "write" -> {
                val file = contained(workspace, requireNotNull(args.getString("path")))
                val bytes = requireNotNull(args.getByteArray("bytes")); require(bytes.size <= 256 * 1024)
                file.parentFile?.mkdirs()
                val staging = File(file.parentFile, ".write-${UUID.randomUUID()}")
                try { staging.writeBytes(bytes); java.nio.file.Files.move(staging.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
                finally { staging.delete() }
                Bundle()
            }
            "startJob" -> synchronized(jobs) {
                check(enabled)
                if (jobs.size >= MAX_JOBS) jobs.values.removeIf { it.task.isDone }
                check(jobs.size < MAX_JOBS) { "Too many jobs" }
                val id = UUID.randomUUID().toString()
                val command = requireNotNull(args.getString("command"))
                val stdout = RuntimeJobOutput()
                val stderr = RuntimeJobOutput()
                val keepAlive = args.getBoolean("keepAlive")
                val task = FutureTask { execute(session, command, 0, id, keepAlive, stdout, stderr) }
                jobs[id] = Job(session, stdout, stderr, task)
                pool.execute(task)
                Bundle().apply { putString("id", id) }
            }
            "queryJob" -> {
                val job = requireNotNull(jobs[args.getString("id")])
                require(job.session == session)
                if (!job.task.isDone) Bundle().apply {
                    putBoolean("running", true)
                    putString("stdout", job.stdout.snapshot())
                    putString("stderr", job.stderr.snapshot())
                } else try { job.task.get() } catch (error: ExecutionException) {
                    Bundle().apply { putBoolean("failed", true); putString("stderr", "Job failed before completion") }
                }
            }
            "stopJob" -> {
                val id = requireNotNull(args.getString("id"))
                val job = requireNotNull(jobs[id])
                require(job.session == session)
                job.task.cancel(true)
                jobs.remove(id, job)
                Bundle()
            }
            else -> throw IllegalArgumentException("Unknown operation")
        }
    }

    /** 导出只打开当前会话文件，描述符通过 Provider.openFile 的返回值转移给调用方。 */
    fun openExportFile(session: String, path: String): android.os.ParcelFileDescriptor {
        check(enabled && marker.isFile)
        val prefix = "/home/workspace/${digest(session.toByteArray())}/"
        require(!path.startsWith("/") || path.startsWith(prefix))
        val workspace = File(root, prefix.trim('/'))
        val file = contained(workspace, path.removePrefix(prefix))
        require(file.isFile && file.length() <= 100L * 1024 * 1024)
        return android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun variables(): JSONObject = preferences.getString("variables", null)?.let { JSONObject(cipher.decryptOrThrow(it)) } ?: JSONObject()

    private fun execute(
        session: String,
        command: String,
        timeout: Long,
        id: String = UUID.randomUUID().toString(),
        keepAlive: Boolean = false,
        stdoutOutput: RuntimeJobOutput = RuntimeJobOutput(),
        stderrOutput: RuntimeJobOutput = RuntimeJobOutput(),
    ): Bundle {
        check(enabled); require(command.length <= 100_000 && timeout >= 0)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        if (network != null) {
            try {
                RuntimeDnsConfig.write(root, connectivity.getLinkProperties(network)?.dnsServers.orEmpty())
            } catch (_: IOException) {
                Log.w(TAG, "DNS refresh failed; using previous configuration")
            }
        }
        val native = context.applicationInfo.nativeLibraryDir
        val workPath = "/home/workspace/" + digest(session.toByteArray())
        val workspace = File(root, workPath.trimStart('/')).apply { mkdirs() }
        val guestPidPath = "$workPath/.gimi-jobs/$id.pid"
        val pidFile = contained(workspace, ".gimi-jobs/$id.pid")
        pidFile.parentFile!!.mkdirs()
        // Android SELinux 禁止硬链接时由 PRoot 模拟，保证 dpkg 等标准工具可写入备份。
        val builder = ProcessBuilder("$native/libproot.so", "--link2symlink", "--kill-on-exit", "-0", "-r", root.absolutePath,
            "-b", "/dev", "-b", "/proc", "-w", workPath, "/bin/sh", "-c", shellScript, "gimi", command, if (keepAlive) "1" else "0", guestPidPath)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", native); put("PROOT_LOADER", "$native/libproot-loader.so")
            put("PROOT_TMP_DIR", context.cacheDir.absolutePath); put("PROOT_NO_SECCOMP", "1")
            put("HOME", workPath); put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
            put("LANG", "C.UTF-8")
            val vars = variables(); vars.keys().forEach { put(it, vars.getString(it)) }
        }
        val process = builder.start()
        val control = RuntimeProcessControl(process, pidFile, guestPidPath)
        processes[id] = control
        if (!enabled || Thread.currentThread().isInterrupted) {
            control.terminate()
            processes.remove(id, control)
            throw IOException("Environment disabled or job stopped")
        }
        val stdout = pool.submit { capture(process.inputStream, stdoutOutput) }
        val stderr = pool.submit { capture(process.errorStream, stderrOutput) }
        try {
            val completed = if (timeout == 0L) { process.waitFor(); true } else process.waitFor(timeout, TimeUnit.MILLISECONDS)
            if (!completed) control.terminate()
            // 先允许读线程消费最终输出，再关闭管道；读流异常时仍保留已捕获内容。
            drainOutput(stdout)
            drainOutput(stderr)
            return Bundle().apply {
                putString("stdout", stdoutOutput.snapshot()); putString("stderr", stderrOutput.snapshot())
                putInt("exitCode", if (completed) process.exitValue() else 124); putBoolean("timedOut", !completed)
            }
        } finally {
            control.terminate()
            runCatching { process.inputStream.close(); process.errorStream.close() }
            stdout.cancel(true)
            stderr.cancel(true)
            processes.remove(id, control)
        }
    }

    private fun drainOutput(future: Future<*>) {
        try { future.get(2, TimeUnit.SECONDS) }
        catch (_: TimeoutException) { }
        catch (_: ExecutionException) { }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    private fun capture(stream: java.io.InputStream, output: RuntimeJobOutput) = stream.reader().use { reader ->
        val buffer = CharArray(4096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            output.append(buffer, count)
        }
    }

    fun install() {
        if (!installing.compareAndSet(false, true)) return
        failed = false; canceled = false
        pool.submit {
            try {
                RootfsInstaller(context).install({ installing.get() }, { progress = it })
                marker.writeText("debian-trixie-arm64"); enabled = true
                context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(RuntimeDocumentsProvider.AUTHORITY), null)
            } catch (error: IOException) {
                // 安装是跨进程后台任务：失败原因必须留痕，否则 UI 只有布尔值无法定位。
                Log.w(TAG, "install failed: ${error.javaClass.simpleName}: ${error.message}")
                failed = !canceled
            } catch (error: Exception) {
                // chmod 等抛出的 ErrnoException 不是 IOException；兜底标记失败避免状态卡在安装中。
                Log.w(TAG, "install crashed: ${error.javaClass.simpleName}: ${error.message}")
                failed = true
            }
            finally { installing.set(false); canceled = false }
        }
    }

    companion object {
        private const val TAG = "RuntimeInstall"
        /** 前台 execute 走 binder call 的超时上限；startJob 传 0 表示无界（可 StopJob）不受此限。 */
        private const val MAX_SYNC_TIMEOUT_MILLIS = 600_000L
        private const val MAX_JOBS = 32
        @Volatile private var instance: RuntimeEngine? = null
        fun get(context: Context): RuntimeEngine = instance ?: synchronized(this) {
            instance ?: RuntimeEngine(context.applicationContext).also { instance = it }
        }
        internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        internal fun contained(root: File, path: String): File {
            val file = File(root, path.trimStart('/')).canonicalFile
            require(file.toPath().startsWith(root.canonicalFile.toPath()))
            return file
        }
    }
}
