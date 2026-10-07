package github.ponyhuang.gimi.domain.modelcatalog.repository

import kotlinx.coroutines.flow.StateFlow

/** 原生引擎的加载阶段，与模型文件的下载状态相互独立。 */
enum class LocalModelLoadPhase { Unloaded, Loading, Ready, Failed }

/** 原生引擎的当前快照，modelId 用于防止旧模型完成加载后解锁新模型的输入。 */
data class LocalModelLoadState(
    val modelId: String? = null,
    val phase: LocalModelLoadPhase = LocalModelLoadPhase.Unloaded,
)

/** 预加载并保留一个本地引擎；SDK、串行推理和原生资源回收由 data 层实现。 */
interface LocalModelRuntime {
    val state: StateFlow<LocalModelLoadState>
    /** 完成后发布 Ready 或 Failed；协程取消必须继续传播。 */
    suspend fun prepare(modelId: String)
    /** 等待进行中的加载/推理退出后释放资源。 */
    suspend fun unload()
    /** 在引擎锁内释放并删除模型，禁止加载与文件移除交错。 */
    suspend fun remove(modelId: String)
    /** ViewModel 销毁时请求释放，不依赖已取消的 ViewModel scope。 */
    fun release()
}
