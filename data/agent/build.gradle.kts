plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "github.ponyhuang.gimi.data.agent"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    implementation(project(":core:common"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:mcp"))
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:toolauthorization"))
    implementation(project(":domain:workfiles"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.anthropic.java)
    implementation(libs.google.adk.kotlin.core)
    implementation(libs.openai.java)
    implementation(libs.okhttp)
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)
    implementation(libs.sentence.embeddings)
    implementation(libs.mcp)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.gson)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.google.adk.kotlin.processor)
    ksp(libs.hilt.android.compiler)
    kapt(libs.objectbox.processor)

    testImplementation(project(":core:testing"))
    testImplementation(libs.google.adk.kotlin.webserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.objectbox.linux)
    testImplementation(libs.objectbox.macos)
    testImplementation(libs.objectbox.windows)
    testImplementation(libs.turbine)
}

ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}

kapt {
    arguments {
        arg(
            "objectbox.modelPath",
            "$projectDir/objectbox-models/default.json",
        )
        arg(
            "objectbox.myObjectBoxPackage",
            "github.ponyhuang.gimi.agent.tools.search",
        )
    }
}
