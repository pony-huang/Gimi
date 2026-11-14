plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.voice"
}

dependencies {
    implementation(project(":core:audio"))
    implementation(project(":core:common"))
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:voice"))
    // 厂商语音适配器（ASR/TTS 协议实现）拆分至 :data:modelaccess，仓库经 domain 接口消费。
    implementation(project(":data:modelaccess"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.gson)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
