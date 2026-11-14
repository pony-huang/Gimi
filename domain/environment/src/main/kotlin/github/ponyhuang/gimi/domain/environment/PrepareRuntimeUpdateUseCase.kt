package github.ponyhuang.gimi.domain.environment

/** 安装入口前重新检查版本；活跃环境未经确认时返回 null，确认后先停止操作再提供 APK。 */
class PrepareRuntimeUpdateUseCase(private val repository: EnvironmentRepository) {
    suspend operator fun invoke(stopRunning: Boolean = false): String? {
        val status = repository.status()
        check(!status.available || status.updateAvailable) { "Runtime update unavailable" }
        if ((status.enabled || status.installing) && !stopRunning) return null
        if (status.enabled || status.installing) repository.setEnabled(false)
        return repository.companionApk()
    }
}
