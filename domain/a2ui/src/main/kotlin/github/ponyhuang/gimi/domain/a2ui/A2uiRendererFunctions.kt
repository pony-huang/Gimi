package github.ponyhuang.gimi.domain.a2ui

/** 当前已挂载的渲染端操作，以会话和 surface 限定调用范围。 */
class A2uiRendererFunctions {
    private val players = mutableMapOf<Pair<String, String>, MutableMap<Any, suspend (Long) -> Boolean>>()

    fun registerMusicPlayer(
        sessionId: String,
        surfaceId: String,
        seek: suspend (Long) -> Boolean,
    ): () -> Unit {
        val key = sessionId to surfaceId
        val registration = Any()
        synchronized(players) { players.getOrPut(key, ::mutableMapOf)[registration] = seek }
        return {
            synchronized(players) {
                val entries = players[key]
                entries?.remove(registration)
                if (entries?.isEmpty() == true) players.remove(key)
            }
        }
    }

    suspend fun seekMusicPlayer(sessionId: String, surfaceId: String, positionMillis: Long): Boolean {
        if (positionMillis < 0) return false
        // 同一 surface 有多个播放器时拒绝歧义，避免跳转到任意一张卡片。
        val seek = synchronized(players) { players[sessionId to surfaceId]?.values?.singleOrNull() }
        return seek?.invoke(positionMillis) ?: false
    }
}
