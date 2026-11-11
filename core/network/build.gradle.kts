plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.core.network"
    testOptions {
        unitTests {
            // 传输内核的失败路径会走 android.util.Log；单测放行默认值，避免 "Stub!" 崩掉回调线程。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:storage"))
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
