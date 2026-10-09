package github.ponyhuang.gimi.data.logging

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.logging.LogRepository
import github.ponyhuang.gimi.domain.logging.LogSnapshot
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 私有 no-backup 目录与系统文档写入适配，磁盘操作均在 IO 调度器执行。 */
@Singleton
class AndroidLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LogRepository {
    internal val store = RollingLogStore(File(context.noBackupFilesDir, "logs"))

    override suspend fun read(): LogSnapshot = withContext(Dispatchers.IO) { store.read() }

    override suspend fun export(destination: String): Unit = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(Uri.parse(destination), "wt")
            ?: throw IOException("Cannot open log document")
        stream.bufferedWriter(Charsets.UTF_8).use { writer -> store.exportTo(writer::write) }
    }
}
