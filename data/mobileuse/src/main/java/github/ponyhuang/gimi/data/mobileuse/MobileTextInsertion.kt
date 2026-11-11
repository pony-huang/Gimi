package github.ponyhuang.gimi.data.mobileuse

/** 插入文本后的完整内容与折叠光标位置；不丢弃原文本的前后部分。 */
internal data class MobileTextInsertion(val text: String, val cursor: Int) {
    companion object {
        /** 非空内容的选区未知时拒绝；已确认的空框只有一个插入位置，索引使用 UTF-16 单位。 */
        fun plan(current: String?, start: Int, end: Int, inserted: String): MobileTextInsertion? {
            // 商店搜索框可在已有输入焦点时仍返回 -1/-1；空内容的唯一合法光标就是 0。
            if (current == "" && start == -1 && end == -1) return MobileTextInsertion(inserted, inserted.length)
            if (current == null || start !in 0..current.length || end !in 0..current.length) return null
            val first = minOf(start, end)
            val last = maxOf(start, end)
            return MobileTextInsertion(current.replaceRange(first, last, inserted), first + inserted.length)
        }
    }
}

/** 区分未写入、完整成功与文字已写入但光标恢复失败，防止部分成功后重复输入或提交。 */
internal enum class MobileTextInputOutcome { REJECTED, INSERTED, CURSOR_UNAVAILABLE, UNCONFIRMED }
