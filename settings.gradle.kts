pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "asssistantai"
include(":app")
include(":domain:modelcatalog")
include(":domain:conversation")
include(":domain:speech")
include(":core:common")
include(":core:audio")
include(":core:testing")
include(":data:modelcatalog")
include(":core:network")
include(":core:database")
include(":data:speech")
include(":data:conversation")
include(":core:designsystem")
include(":domain:mcp")
include(":domain:workfiles")
include(":domain:permissions")
include(":domain:toolauthorization")
include(":domain:skills")
include(":data:mcp")
include(":data:workfiles")
include(":data:permissions")
include(":data:toolauthorization")
include(":data:skills")
include(":feature:modelsettings")
include(":feature:mcp")
include(":feature:workfiles")
include(":feature:permissions")
include(":feature:toolauthorization")
include(":feature:voicewake")
include(":feature:settings")
include(":feature:skills")
include(":feature:chat")
