package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/**
 * 外部应用回传给工具的结果值。
 *
 * @property resultCode 外部应用返回的结果码，[RESULT_OK] 表示用户完成了一次选择。
 * @property dataUri 结果数据的主 Uri 字符串；外部应用未携带数据时为 null。
 * @property uris 结果中携带的全部 Uri（主数据 + clipData 去重），供多选场景使用。
 */
internal data class ToolActivityResult(
    val resultCode: Int,
    val dataUri: String?,
    val uris: List<String>,
)

/**
 * 工具等待外部应用返回结果的登记表。
 *
 * [IntentActionQueue.requestForResult] 先按请求 ID 登记"待启动意图 + 结果投递点"，再拉起
 * [ToolResultActivity]；Activity 用同一 ID 取回登记项后以自身身份发起
 * `startActivityForResult`，把结果补回挂起等待的工具协程。登记项要么被 Activity 取走，
 * 要么在取消/超时清理时移除，保证不会被后续请求误用。
 */
internal object ToolResultRegistry {

    /** 一次待中转的外部跳转。 */
    internal class PendingToolResultLaunch(
        val intent: Intent,
        val result: CompletableDeferred<ToolActivityResult>,
    )

    private val pending = ConcurrentHashMap<String, PendingToolResultLaunch>()

    fun put(id: String, launch: PendingToolResultLaunch) {
        pending[id] = launch
    }

    /** 取走登记项；Activity 创建时调用，之后结果只投递给取走它的这一次跳转。 */
    fun consume(id: String): PendingToolResultLaunch? = pending.remove(id)

    /** 只读查看登记项；Activity 因配置变更重建时用同一 ID 重新挂接结果投递点。 */
    fun peek(id: String): PendingToolResultLaunch? = pending[id]
}

/**
 * 透明中转 Activity：以自身身份向外部应用发起 `startActivityForResult`，从而拿到
 * 选择结果。应用 Context 直接 `startActivity` 收不到任何返回值，因此"要从意图获取值"
 * 的系统工具（文件选择、联系人选择等）都必须经过这里中转一跳。
 *
 * 进程被回收重建或登记项已丢失时结果无从投递，直接退出，等待方会按超时收到
 * cancelled 状态。
 */
class ToolResultActivity : ComponentActivity() {

    private var pending: ToolResultRegistry.PendingToolResultLaunch? = null

    private val resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { outcome ->
        pending?.result?.complete(
            ToolActivityResult(
                resultCode = outcome.resultCode,
                dataUri = outcome.data?.dataString,
                uris = collectResultUris(outcome.data),
            ),
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        val launch = if (savedInstanceState == null) {
            requestId?.let(ToolResultRegistry::consume)
        } else {
            // 重建（如旋转屏幕）时不重复拉起外部应用，只重新挂接结果投递点。
            requestId?.let(ToolResultRegistry::peek)
        }
        if (launch == null) {
            finish()
            return
        }
        pending = launch
        if (savedInstanceState == null) {
            resultLauncher.launch(launch.intent)
        }
    }

    private fun collectResultUris(intent: Intent?): List<String> = buildList {
        intent?.data?.let { add(it.toString()) }
        intent?.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index)?.uri?.let { uri -> add(uri.toString()) }
            }
        }
    }.distinct()

    internal companion object {
        internal const val EXTRA_REQUEST_ID = "github.ponyhuang.gimi.result.REQUEST_ID"
    }
}
