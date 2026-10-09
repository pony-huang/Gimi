package github.ponyhuang.gimi.data.logging

import android.content.Context
import android.os.Process
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 冷启动采集当前进程的已有 Android 日志；无需 READ_LOGS 或存储权限。 */
@Singleton
class AppLogRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AndroidLogRepository,
) {
    private var started = false

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "app-log-cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<LogCleanupWorker>(1, TimeUnit.HOURS).build(),
        )
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    repository.store.cleanup()
                } catch (_: IOException) {
                    // 记录器自身故障不打回 Logcat，避免磁盘故障造成递归日志风暴。
                } catch (_: SecurityException) {
                    // 应用其余功能不依赖诊断日志目录可写。
                }
                delay(60_000)
            }
        }
        val cursor = LogcatCursor(System.currentTimeMillis())
        scope.launch(Dispatchers.IO) {
            val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
            while (isActive) {
                var process: java.lang.Process? = null
                try {
                    cursor.beginSession()
                    // epoch 时间用于严格剔除旧进程/旧缓冲区记录；INFO 起步控制高频调试输出。
                    process = ProcessBuilder(
                        "logcat", "--pid=${Process.myPid()}", "-v", "epoch",
                        "-T", cursor.since(), "*:I",
                    ).redirectErrorStream(true).start()
                    process.inputStream.bufferedReader().use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            val timestamp = cursor.timestamp(line) ?: continue
                            val formatted = dateFormat.format(Instant.ofEpochMilli(timestamp))
                            repository.store.append("$formatted ${line.trimStart().substringAfter(' ').trimStart()}", timestamp)
                        }
                    }
                } catch (_: IOException) {
                    // 采集失败延迟重启，不阻塞 UI，也不反复写入失败日志。
                } catch (_: SecurityException) {
                    // 部分系统限制 Logcat；启动标记和存储仍可正常查看/导出。
                } finally {
                    process?.destroy()
                }
                delay(30_000)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
                repository.store.append("$formatted I Gimi: Application started", timestamp)
            } catch (_: IOException) {
                // 诊断写入不可用时不影响冷启动。
            } catch (_: SecurityException) {
                // 私有目录不可写时不影响冷启动。
            }
        }
    }
}
