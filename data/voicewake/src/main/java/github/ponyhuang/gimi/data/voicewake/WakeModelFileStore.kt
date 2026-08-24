package github.ponyhuang.gimi.data.voicewake

import java.io.File

/** 管理已安装唤醒模型的本地目录。 */
internal class WakeModelFileStore(
    private val rootDir: File,
    private val deleteDirectory: (File) -> Boolean = { it.deleteRecursively() },
) {
    fun installedDir(modelId: String): File = File(rootDir, modelId)

    fun remove(modelId: String): Boolean {
        val directory = installedDir(modelId)
        return !directory.exists() || deleteDirectory(directory) && !directory.exists()
    }
}
