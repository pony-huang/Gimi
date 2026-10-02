package github.ponyhuang.gimi.data.mobileuse

import android.view.Surface
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult

/** Android 预览输出端口。Surface 只在 app 宿主与 data 网关之间传递，不进入领域或 ViewModel。 */
interface MobileDisplayPreviewGateway {
    suspend fun attachPreview(sessionId: String, bindingId: String, surface: Surface, width: Int, height: Int): MobileUseResult
    suspend fun detachPreview(sessionId: String, bindingId: String)
}
