import java.util.zip.ZipFile

plugins {
    id("gimi.android.application")
    id("gimi.android.compose")
    id("gimi.android.hilt")
    alias(libs.plugins.androidx.baselineprofile)
}

// 配套 APK 随主 App 交付，按构建类型独立生成，安装后使用独立 UID 执行代码。
listOf("debug", "release").forEach { buildType ->
    val capitalized = buildType.replaceFirstChar { it.uppercase() }
    val packageRuntime = tasks.register<Copy>("package${capitalized}EnvironmentRuntime") {
        dependsOn(":environment-runtime:assemble$capitalized")
        from(project(":environment-runtime").layout.buildDirectory.dir("outputs/apk/$buildType")) {
            include("environment-runtime-$buildType.apk")
            rename { "environment-runtime.apk" }
        }
        into(layout.buildDirectory.dir("generated/environmentAssets/$buildType"))
    }
    // 惰性匹配源集：benchmark 等自定义 buildType 的 SourceSet 在下方 android{} 块才注册，
    // 顶层脚本执行到此处时尚不存在，getByName 会直接失败。
    android.sourceSets.matching { it.name == buildType }.configureEach {
        assets.srcDir(
            layout.buildDirectory.dir("generated/environmentAssets/$buildType").get().asFile,
        )
    }
    // 资产源集的直读消费方（资产合并、lint 分析与模型生成）都需先拷贝配套 APK，
    // 否则 Gradle 隐式依赖校验会构建失败；lintVital 仅 release 存在，lint 分析按变体命名。
    tasks.matching {
        it.name in setOf(
            "merge${capitalized}Assets",
            "lintVitalAnalyze$capitalized",
            "lintAnalyze$capitalized",
            "generate${capitalized}LintVitalReportModel",
            "generate${capitalized}LintModel",
        )
    }.configureEach { dependsOn(packageRuntime) }

    // 产物级防呆：配置缓存/增量状态异常时曾出现“构建成功但 APK 缺配套运行组件”，
    // 因此绕过中间产物，直接校验最终 APK 的 assets；缺失立即让构建失败。
    val verifyRuntimeAsset = tasks.register("verify${capitalized}RuntimeAsset") {
        group = "verification"
        description = "Verifies assembled $buildType APKs bundle assets/environment-runtime.apk."
        dependsOn("package$capitalized")
        val apkDir = layout.buildDirectory.dir("outputs/apk/$buildType")
        doLast {
            val apks = apkDir.get().asFile
                .listFiles { file -> file.isFile && file.extension == "apk" }
                .orEmpty()
                .sortedBy { it.name }
            check(apks.isNotEmpty()) { "No $buildType APK found under ${apkDir.get().asFile.path}." }
            apks.forEach { apk ->
                ZipFile(apk).use { zip ->
                    check(zip.getEntry("assets/environment-runtime.apk") != null) {
                        "${apk.name} is missing assets/environment-runtime.apk; " +
                            "the bundled runtime was not packaged, refusing to produce an incomplete APK."
                    }
                }
            }
        }
    }
    tasks.matching { it.name in setOf("assemble$capitalized", "install$capitalized") }
        .configureEach { dependsOn(verifyRuntimeAsset) }
}

android {
    namespace = "github.ponyhuang.gimi"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi"
        // App 与插件的版本由 gimi.android.application 统一读取。

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
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
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
    // Baseline 记录现存 lint 问题，lint 继续对新问题报错。
    lint {
        baseline = file("lint-baseline.xml")
        // AGP 9.x + lifecycle 2.10+ 触发；Kotlin 2.4.0 注解空性判定已被编译器处理。
        disable += "InvalidNullabilityAnnotation"
    }
    packaging {
        jniLibs {
            // tasks-text 1.0.0 同时打包 TextGenAI；工具搜索仅使用 TextEmbedder。
            excludes += "**/libmediapipe_tasks_textgenai_jni.so"
        }
        resources {
            // 新版 Netty 各平台 QUIC 包携带同名许可证，保留一份避免 Java 资源合并冲突。
            pickFirsts += "META-INF/license/**"
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                // Jakarta CDI bean 归档标记（a2a-java 各 jar 携带），仅服务端容器使用，
                // Android 运行时无用；三个 jar 同名文件会阻断 Java 资源合并。
                "META-INF/beans.xml",
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
    implementation(project(":feature:environment"))
    implementation(project(":data:environment"))
    implementation(project(":data:a2ui"))
    implementation(project(":feature:sandbox"))
    implementation(project(":data:sandbox"))
    // Provider 由主 App Manifest 注册，依赖归属 composition root，确保 lint 能解析该组件。
    implementation(libs.shizuku.provider)
    implementation(project(":domain:appearance"))
    implementation(project(":domain:mobileuse"))
    implementation(project(":domain:permissions"))
    implementation(project(":domain:plugin"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:voice"))
    implementation(project(":domain:appupdate"))
    implementation(project(":core:audio"))
    implementation(project(":data:assistant"))
    implementation(project(":domain:assistant"))
    implementation(project(":domain:recommendation"))
    implementation(project(":domain:memory"))
    implementation(project(":core:common"))
    implementation(project(":data:modelcatalog"))
    implementation(project(":data:voice"))
    implementation(project(":data:conversation"))
    implementation(project(":data:agent"))
    implementation(project(":data:appearance"))
    implementation(project(":data:mcp"))
    implementation(project(":data:a2a"))
    implementation(project(":data:appfunction"))
    implementation(project(":data:workfiles"))
    implementation(project(":data:permissions"))
    implementation(project(":data:toolauthorization"))
    implementation(project(":data:skills"))
    implementation(project(":data:appupdate"))
    implementation(project(":data:logging"))
    implementation(project(":data:plugin"))
    implementation(project(":data:recommendation"))
    implementation(project(":data:memory"))
    implementation(project(":data:workspace"))
    implementation(project(":data:mobileuse"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:notifications"))
    implementation(project(":feature:modelsettings"))
    implementation(project(":feature:mcp"))
    implementation(project(":feature:a2a"))
    implementation(project(":feature:appfunction"))
    implementation(project(":feature:permissions"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:plugin"))
    implementation(project(":feature:skills"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:recommendation"))
    implementation(project(":feature:memory"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:mobileuse"))
    testImplementation(project(":core:testing"))
    testImplementation(project(":data:modelaccess"))
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
        // baselineprofile 插件自建的基准变体同样跑 R8，需一并排除。
        "benchmarkReleaseRuntimeClasspath",
        "benchmarkReleaseCompileClasspath",
        "benchmarkReleaseRuntimeOnly",
        "nonMinifiedReleaseRuntimeClasspath",
        "nonMinifiedReleaseCompileClasspath",
        "nonMinifiedReleaseRuntimeOnly",
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
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    // 基线配置生产者：生成任务经 :baselineprofile 连接运行后回写 app/src/main/baseline-prof.txt。
    baselineProfile(project(":baselineprofile"))
    testImplementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // compose 测试构件无版本号，跟随 BOM 对齐。
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    kspAndroidTest(libs.hilt.android.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// androidx.test 系（junit 1.3.0）传递 concurrent-futures 1.2.0，与主图 androidx.core
// 钉住的 1.1.0 在 AGP 9 的一致性解析下互相冲突；统一对齐到主图已解析的 1.1.0。
configurations.configureEach {
    resolutionStrategy {
        force("androidx.concurrent:concurrent-futures:1.1.0")
        force("androidx.concurrent:concurrent-futures-ktx:1.1.0")
    }
}
