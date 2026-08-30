plugins {
    id("gimi.plugin.application")
}

android {
    namespace = "github.ponyhuang.gimi.plugin.example"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.example"
    }
}

dependencies {
    // compileOnly（provided）：插件 APK 不打包 SPI 与 ADK，运行时由宿主类加载器提供，
    // 保证 AgentPlugin / Plugin 类身份与宿主一致。compileOnly 非传递，两者都需显式声明。
    // 后续 :plugin-api 拆到独立仓库并发布后，只需把 project(":plugin-api") 换成 Maven 坐标。
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
}
