plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "github.ponyhuang.gimi.data.agent"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Baseline 记录现存 lint 问题（如 LocationTool 的 MissingPermission），
    // lint 继续对新问题报错。
    lint {
        baseline = file("lint-baseline.xml")
    }
    // ADK 的 LoggerFactory 在 JVM 单测里会打到 android.util.Log。
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                val objectBoxTestWorkingDir = rootProject.file("temp/objectbox-tests")
                it.workingDir = objectBoxTestWorkingDir
                it.doFirst {
                    objectBoxTestWorkingDir.mkdirs()
                }
            }
        }
    }
}

dependencies {
    implementation(project(":domain:a2ui"))
    implementation(project(":domain:skills"))
    implementation(project(":domain:sandbox"))
    implementation(project(":domain:mobileuse"))
    implementation(project(":domain:recommendation"))
    implementation(project(":core:common"))
    implementation(project(":core:storage"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:mcp"))
    implementation(project(":domain:a2a"))
    implementation(project(":domain:appfunction"))
    implementation(libs.androidx.appfunctions)
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:toolauthorization"))
    implementation(project(":domain:plugin"))
    implementation(project(":plugin-api"))
    implementation(project(":domain:workfiles"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    implementation(libs.google.adk.kotlin.core) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }
    // A2A 远程 agent：ADK 的 A2A 客户端封装 + 底层线协议 SDK + Android 传输实现。
    // sdk-client 需显式声明：google-adk-kotlin-a2a 的 API 签名（AgentCard/A2AHttpClient）
    // 引用其类型，但其 POM 只在 runtime variant 声明该依赖，不补则编译期缺符号。
    implementation(libs.google.adk.kotlin.a2a)
    implementation(libs.a2a.java.sdk.client)
    implementation(libs.a2a.java.sdk.http.client.android)
    // a2a-java 的 JSON-RPC 线格式化（spec-grpc JSONRPCUtils）依赖 protobuf.util.JsonFormat，
    // 其 POM 把 protobuf-java-util 声明为 provided，由本模块显式补齐；版本与传递的
    // protobuf-java 4.35.0 对齐。
    implementation(libs.protobuf.java.util)
    implementation(libs.google.adk.kotlin.integrations.openai)
    implementation(libs.okhttp)
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)
    // mediapipe 传递的 protobuf-javalite 与 a2a-java spec-grpc 需要的完整版 protobuf-java
    // 同包冲突；完整版包含全部 lite 运行时类，统一到完整版对 mediapipe 行为无影响。
    implementation(libs.mediapipe.tasks.text) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(libs.mcp.kotlin.client)
    // 官方 ADK Android MCP 将 SDK 与引擎声明为 compileOnly，由消费模块明确提供。
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.google.adk.kotlin.processor)
    kapt(libs.objectbox.processor)

    // 仅 debug 变体携带 ADK Development WebServer（Ktor/Netty 服务端 + Dev UI 静态资源），
    // 供 PC 浏览器经局域网观察 agent 会话；release 构建不含任何 webserver 类。
    // webserver 把 opentelemetry-sdk 声明为 runtime scope，补 debug 编译期依赖以消除
    // ApiServerSpanExporter 超类型不可见的告警。
    debugImplementation(libs.google.adk.kotlin.webserver) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }
    debugImplementation(libs.opentelemetry.sdk)

    testImplementation(project(":core:testing"))
    // 验证目录实现与 ADK 适配的边界；运行时只依赖 domain 契约。
    testImplementation(project(":data:modelcatalog"))
    testImplementation(libs.google.adk.kotlin.webserver) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.objectbox.linux)
    testImplementation(libs.objectbox.macos)
    testImplementation(libs.objectbox.windows)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
}

kapt {
    arguments {
        arg(
            "objectbox.modelPath",
            "$projectDir/objectbox-models/default.json",
        )
        arg(
            "objectbox.myObjectBoxPackage",
            "github.ponyhuang.gimi.data.agent.tools.search",
        )
    }
}
