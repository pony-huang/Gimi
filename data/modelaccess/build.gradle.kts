plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
    // DeepSeek Files 上传响应解析用 kotlinx 序列化。
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "github.ponyhuang.gimi.data.modelaccess"
    // 与 :data:agent 同理：ADK LoggerFactory 在 JVM 单测里会打到 android.util.Log。
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":domain:modelaccess"))
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:voice"))
    // 会话域的厂商文件引用（gimi-file://）契约与附件模型由转换器消费。
    implementation(project(":domain:conversation"))
    implementation(project(":core:common"))
    // 厂商文件上传暂存目录走受管目录声明（ManagedDirectorySpec + AppDirectoryResolver）。
    implementation(project(":core:storage"))
    // 实时模型/连接探针与 WS 语音适配器共享传输内核（VoiceWsKernel）与音频格式契约。
    implementation(project(":core:network"))
    implementation(project(":core:audio"))

    implementation(libs.anthropic.java)
    implementation(libs.google.adk.kotlin.core) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }
    // 文本模型的 OpenAI 兼容协议走 ADK 官方集成（ChatCompletions）。
    implementation(libs.google.adk.kotlin.integrations.openai)
    // fork 的 openaicompatible 适配器沿用 ADK 的 Ktor 传输层，显式声明以补齐编译类路径。
    implementation(libs.ktor.client.okhttp)
    implementation(libs.okhttp)
    // OpenAI Realtime 协议沿用语音链路惯例：Gson JsonObject 手工构建/解析。
    implementation(libs.gson)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
