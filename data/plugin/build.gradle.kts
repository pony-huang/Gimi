plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "github.ponyhuang.gimi.data.plugin"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // api：LoadedPlugin 公开暴露 AgentPlugin，须把 :plugin-api 传给下游编译类路径。
    api(project(":plugin-api"))
    implementation(project(":domain:plugin"))
    implementation(libs.hilt.android)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
}
