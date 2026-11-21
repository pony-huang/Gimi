package github.ponyhuang.gimi

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 仅 debug 构建包含的 Compose 测试宿主：@AndroidEntryPoint 使
 * createAndroidComposeRule 场景下的 hiltViewModel() 拿到 Hilt 工厂，
 * 且该 Activity 随主 APK 合并，运行在应用进程内（androidTest 清单里的
 * Activity 会落在独立测试进程，被 ActivityScenario 拒绝）。
 */
@AndroidEntryPoint
class DebugTestHostActivity : ComponentActivity()
