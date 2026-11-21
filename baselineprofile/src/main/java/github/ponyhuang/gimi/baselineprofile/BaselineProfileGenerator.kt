package github.ponyhuang.gimi.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * 生成基线配置：覆盖冷启动到首帧可交互的关键路径。
 *
 * 产物由 baselineprofile 插件经 `:app:copyReleaseBaselineProfileIntoSrc` 写入
 * `app/src/main/baseline-prof.txt` 并提交进仓；启动路径（Application/MainActivity/
 * 首屏组合）变更后需本地重新生成。连接真机或模拟器执行：
 * `ANDROID_SERIAL=<serial> ./gradlew :app:generateReleaseBaselineProfile`。
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect("github.ponyhuang.gimi") {
            pressHome()
            startActivityAndWait()
            // 首帧完成后短暂等待：让首屏组合与首轮流帧进入配置文件。
            device.waitForIdle()
        }
    }
}
