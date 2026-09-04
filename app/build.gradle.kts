plugins {
    id("gimi.android.application")
    id("gimi.android.compose")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi"
        // CI 发布流程通过 -P 注入版本（见 .github/workflows/release.yml）；
        // 未注入时使用本地开发默认值。
        versionCode = providers.gradleProperty("releaseVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("releaseVersionName").getOrElse("0.6.3") // x-release-please-version

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // 发布密钥库由 CI 以 -P 属性注入；本地无配置时 release 构建回退到 debug 签名，
            // 保证任何人都能构建出可安装的 release APK（开源项目无商店分发要求）。
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 签名优先级：CI -P 注入 > IDE「Generate Signed APK」向导注入 > debug 回退。
            // 向导打包时 AGP 注入 android.injected.signing.* 属性，此时不指定 signingConfig，
            // 交由 AGP 应用向导选择的密钥库（本地与 CI 使用同一密钥库，签名一致）。
            val hasCiSigning = providers.gradleProperty("releaseStoreFile").isPresent
            val hasIdeSigning = providers.gradleProperty("android.injected.signing.store.file").isPresent
            signingConfig = when {
                hasCiSigning -> signingConfigs.getByName("release")
                hasIdeSigning -> null
                else -> signingConfigs.getByName("debug")
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    // Baseline 记录现存 lint 问题（如 BluetoothVoiceService 蓝牙权限检查），lint 继续对新问题报错。
    lint {
        baseline = file("lint-baseline.xml")
    }
    packaging {
        jniLibs {
            // tasks-text 1.0.0 同时打包 TextGenAI；工具搜索仅使用 TextEmbedder。
            excludes += "**/libmediapipe_tasks_textgenai_jni.so"
        }
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                // GraalVM native-image metadata shipped by google-genai, google-auth,
                // google-http-client, jansi, opentelemetry. Useless on Android runtime.
                "META-INF/native-image/**",
                "META-INF/proguard/**",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(project(":domain:appearance"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:speech"))
    implementation(project(":core:audio"))
    implementation(project(":data:assistant"))
    implementation(project(":domain:assistant"))
    implementation(project(":domain:recommendation"))
    implementation(project(":domain:memory"))
    implementation(project(":core:common"))
    implementation(project(":data:modelcatalog"))
    implementation(project(":data:speech"))
    implementation(project(":data:voicewake"))
    implementation(project(":data:conversation"))
    implementation(project(":data:agent"))
    implementation(project(":data:appearance"))
    implementation(project(":data:mcp"))
    implementation(project(":data:workfiles"))
    implementation(project(":data:permissions"))
    implementation(project(":data:toolauthorization"))
    implementation(project(":data:skills"))
    implementation(project(":data:appupdate"))
    implementation(project(":data:plugin"))
    implementation(project(":data:recommendation"))
    implementation(project(":data:memory"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:modelsettings"))
    implementation(project(":feature:mcp"))
    implementation(project(":feature:workfiles"))
    implementation(project(":feature:permissions"))
    implementation(project(":feature:toolauthorization"))
    implementation(project(":feature:voicewake"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:plugin"))
    implementation(project(":feature:skills"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:recommendation"))
    implementation(project(":feature:memory"))
    testImplementation(project(":core:testing"))
    // Drop kxml2 from the release runtime configurations only. Android's
    // framework already provides android.content.res.XmlResourceParser which
    // implements the same org.xmlpull.v1.XmlPullParser interface, so keeping
    // both jars in the runtime classpath makes R8 fail with "Library class
    // implements program class". Excluding kxml2 from runtime/compile lets
    // the Android implementation take over at runtime, while leaving it on
    // lint/testAndroid configurations so build-time tooling keeps working.
    val kxml2ExcludedConfigurations = setOf(
        "releaseRuntimeClasspath",
        "releaseCompileClasspath",
        "releaseRuntimeOnly",
    )
    configurations.configureEach {
        if (name in kxml2ExcludedConfigurations) {
            exclude(group = "net.sf.kxml", module = "kxml2")
        }
    }

    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

}
