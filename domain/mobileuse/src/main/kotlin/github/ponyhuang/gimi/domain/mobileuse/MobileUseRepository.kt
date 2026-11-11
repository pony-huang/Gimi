package github.ponyhuang.gimi.domain.mobileuse

import kotlinx.coroutines.flow.StateFlow

/** 副屏设置与人工窗口控制；不作为 Agent 手机动作契约。 */
interface MobileUseRepository {
    val enabled: StateFlow<Boolean>
    val displaySession: StateFlow<MobileDisplaySession?>
    val smallWindowEnabled: StateFlow<Boolean>
    suspend fun setSmallWindowEnabled(enabled: Boolean)
    /** 关闭时释放副屏资源并拒绝后续操作。 */
    suspend fun setEnabled(enabled: Boolean)
    fun availability(): MobileUseAvailability
    fun requestPermission(requestCode: Int)
    fun textInputAvailable(): Boolean
    suspend fun closeSession(sessionId: String): MobileUseResult
    suspend fun manualTouch(sessionId: String, touch: MobileTouch): MobileUseResult
    suspend fun manualBack(sessionId: String): MobileUseResult
    suspend fun manualRotate(sessionId: String): MobileUseResult
}
