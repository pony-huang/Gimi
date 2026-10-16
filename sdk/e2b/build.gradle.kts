plugins {
    kotlin("jvm") version "2.2.20"
    `java-library`
    `maven-publish`
}

group = "dev.e2b.community"
version = "0.1.0-SNAPSHOT"

kotlin { jvmToolchain(21) }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
publishing {
    publications {
        create<MavenPublication>("sdk") {
            from(components["java"])
            artifactId = "e2b-sdk"
            pom {
                name.set("E2B Kotlin/JVM SDK")
                description.set("Unofficial Kotlin and Java client for E2B sandboxes")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}
