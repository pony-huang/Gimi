package github.ponyhuang.gimi.data.mobileuse

/** 无画面时先重试，再重接截图 Surface；不销毁仍在运行应用的副屏。 */
internal object MobileCaptureRecovery {
    fun capture(captureFrame: () -> ByteArray?, recoverSurface: () -> Unit): ByteArray? {
        repeat(2) { captureFrame()?.let { return it } }
        recoverSurface()
        return captureFrame()
    }
}
