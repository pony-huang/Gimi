plugins {
    id("gimi.android.library")
    id("gimi.android.compose")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.feature.assistant"
    // 当前模块没有单元测试，避免资源打包生成的 R 类触发 Gradle 的空测试检查。
    testOptions.unitTests.isIncludeAndroidResources = false
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":domain:assistant"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
}
