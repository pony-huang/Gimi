package github.ponyhuang.gimi.data.appupdate.apk

import github.ponyhuang.gimi.domain.appupdate.model.ApkAsset

/**
 * 按设备 ABI 从 release assets 中选择 APK：
 * 优先按 Build.SUPPORTED_ABIS 顺序精确匹配 "-<abi>.apk" 后缀，
 * 兜底 universal，都无则返回 null。
 */
internal object ApkAssetSelector {

    fun select(assets: List<ApkAsset>, deviceAbis: List<String>): ApkAsset? {
        for (abi in deviceAbis) {
            assets.firstOrNull { it.name.endsWith("-$abi.apk") }?.let { return it }
        }
        return assets.firstOrNull { it.name.endsWith("-universal.apk") }
    }
}
