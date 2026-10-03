package github.ponyhuang.gimi.domain.mobileuse

/** 原始截图像素坐标中的元素矩形，右侧和底部不包含在范围内。 */
data class MobileBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom
    val centerX: Int get() = left + (right - left) / 2
    val centerY: Int get() = top + (bottom - top) / 2
}

/**
 * 观察快照中的可见元素；OCR 文本框不具有已确认的控件语义。
 * @property id 仅在所属观察中有效的元素编号。
 * @property source accessibility 或 ocr；不得将 OCR 文本框视为原生按钮。
 * @property bounds 原始截图坐标中的边界。
 * @property actions 可执行动作：click、set_text 或 OCR 的坐标 tap。
 * @property clickable 无障碍报告的可点击性，OCR 无法确定时为 null。
 * @property parentId 同一快照内的父节点编号；父节点可能因裁剪未被返回。
 */
data class MobileElement(
    val id: String,
    val source: String,
    val bounds: MobileBounds,
    val text: String? = null,
    val description: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val windowId: Int? = null,
    val windowLayer: Int? = null,
    val parentId: String? = null,
    val clickable: Boolean? = null,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val actions: List<String> = emptyList(),
    val confidence: Float? = null,
)

/**
 * 有时限的画面观察结果，不承诺页面业务加载完成。
 * @property id 随机观察编号；后续动作必须引用最新编号。
 * @property state settled、timeout 或 frame_unavailable。
 * @property reason 稳定或超时的具体原因；空树与疑似空白不等同于加载中。
 * @property frameAgeMs 最近有效帧距返回时的时间；缓存帧不是动作完成证明。
 * @property frameSequence 当前截图采集序号。
 * @property nodesStatus 节点可用性及 OCR 兜底状态。
 * @property truncated 元素列表或遍历是否达到上限。
 * @property actionModes 当前观察允许的定位方式：native_elements、pixel_coordinates、swipe_coordinates、back；不要求整页静止。
 */
data class MobileObservation(
    val id: String,
    val state: String,
    val reason: String,
    val frameAgeMs: Long?,
    val frameSequence: Long?,
    val nodesStatus: String,
    val elements: List<MobileElement> = emptyList(),
    val truncated: Boolean = false,
    val actionModes: List<String> = emptyList(),
)
