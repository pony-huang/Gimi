plugins {
    id("gimi.android.library")
    id("gimi.android.compose")
    id("gimi.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "github.ponyhuang.gimi.feature.recommendation"
    // 当前模块没有单元测试，避免资源打包生成的 R 类触发 Gradle 的空测试检查。
    testOptions.unitTests.isIncludeAndroidResources = false
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":domain:recommendation"))
    implementation(project(":domain:modelcatalog"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
}
