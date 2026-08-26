plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "github.ponyhuang.gimi.pluginapi"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 以 api 暴露 ADK Plugin 类型：宿主与插件共享同一份 Plugin 接口，
    // 保证 DexClassLoader 下类身份一致（避免 ClassCastException）。
    // 排除 ADK 自带的 MCP SDK，宿主用独立的 mcp-kotlin-client（与 data:agent 一致）。
    api(libs.google.adk.kotlin.core) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // JVM 单测里 Android 的 org.json 是 not mocked 的桩，需用 JVM 版。
    testImplementation(libs.org.json)
}
