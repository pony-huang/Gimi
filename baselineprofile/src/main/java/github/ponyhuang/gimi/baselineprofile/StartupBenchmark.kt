package github.ponyhuang.gimi.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * 冷启动耗时基准：对比「无编译优化」与「基线配置」两种编译模式，
 * 验证 baseline-prof.txt 的实际收益并防回归。
 *
 * 需要主 App 的 benchmark 变体（不混淆、debug 签名），连接设备执行：
 * `ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedBenchmarkAndroidTest`。
 */
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfiles() = startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "github.ponyhuang.gimi",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
