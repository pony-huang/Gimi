plugins {
    id("gimi.kotlin.jvm.library")
}

dependencies {
    // ApiProtocol/FileIngestMode/ModelSelection 出现在公开 API 签名中，需传递给消费方编译类路径
    api(project(":domain:modelcatalog"))
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
