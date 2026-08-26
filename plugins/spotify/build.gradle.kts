plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "github.ponyhuang.gimi.plugin.spotify"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.spotify"
        minSdk = 34
        targetSdk = 37
        // CI 发布流程通过 -P 注入版本（见 .github/workflows/release.yml）；
        // 未注入时使用本地开发默认值。版本跟随主 App Release 对齐。
        versionCode = providers.gradleProperty("releaseVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("releaseVersionName").getOrElse("1.0")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    signingConfigs {
        create("release") {
            // 发布密钥库由 CI 以 -P 属性注入（同 app/build.gradle.kts）；本地无配置时 release 构建回退 debug 签名。
            val storeFilePath = providers.gradleProperty("releaseStoreFile").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("releaseStorePassword").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            }
        }
    }
    buildTypes {
        release {
            // 插件 APK 需签名才能 pm install；优先级：CI -P 注入 > debug 回退。
            val hasCiSigning = providers.gradleProperty("releaseStoreFile").isPresent
            signingConfig = if (hasCiSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // compileOnly（provided）：插件 APK 不打包 SPI/ADK/coroutines，运行时由宿主类加载器提供。
    // compileOnly 非传递，三个都需显式声明。
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
    compileOnly(libs.kotlinx.coroutines.core)
    // androidx.core.net.toUri 运行时由宿主提供。
    compileOnly(libs.androidx.core.ktx)

    // 单测里用 JVM 版 org.json（Android 的 org.json 在 JVM 单测里是抛 not mocked 的桩）。
    testImplementation(project(":plugin-api"))
    testImplementation(libs.google.adk.kotlin.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.org.json)
}
