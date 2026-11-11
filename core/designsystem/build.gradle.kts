plugins {
    id("gimi.android.library")
    id("gimi.android.compose")
}

android {
    namespace = "github.ponyhuang.gimi.core.designsystem"
}

dependencies {
    // VoicePickerPage / VoicePickerDialog 是经 AGENTS.md 豁免的共享业务组件：公开签名使用 domain:voice 的
    // TtsVoice/VoiceCategory/VoiceGender，需要把领域类型传递给消费方编译类路径。
    api(project(":domain:voice"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.coil3)
    implementation(libs.multiplatform.markdown.renderer.code)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
