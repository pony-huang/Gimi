package github.ponyhuang.gimi.data.voicewake

/** 决定当前音频块是否可以进入唤醒词检测。 */
internal object WakeTriggerPolicy {
    fun canTrigger(
        status: BluetoothVoiceStatus,
        currentChatVisible: Boolean,
        cooldownElapsed: Boolean,
    ): Boolean =
        status == BluetoothVoiceStatus.Listening &&
            !currentChatVisible &&
            cooldownElapsed
}
