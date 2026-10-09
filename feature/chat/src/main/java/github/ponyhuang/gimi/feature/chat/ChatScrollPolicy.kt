package github.ponyhuang.gimi.feature.chat

/** 恢复/更新内容时尊重阅读位置；首次进入、主动切换或发送新消息才重新定位。 */
internal fun shouldScrollChatToLatest(
    isInitialPositioning: Boolean,
    explicitRequestChanged: Boolean,
    latestUserMessageChanged: Boolean,
    followsLatest: Boolean,
): Boolean = isInitialPositioning || explicitRequestChanged || latestUserMessageChanged || followsLatest
