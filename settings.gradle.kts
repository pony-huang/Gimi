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
include(":domain:assistant")
include(":domain:conversation")
include(":domain:speech")
include(":core:common")
include(":core:audio")
include(":core:testing")
include(":core:security")
include(":data:modelcatalog")
include(":core:network")
include(":data:speech")
include(":data:voicewake")
include(":data:assistant")
include(":data:conversation")
include(":core:designsystem")
include(":domain:mcp")
include(":domain:workfiles")
include(":domain:permissions")
include(":domain:toolauthorization")
include(":domain:skills")
include(":domain:appupdate")
include(":domain:plugin")
include(":domain:recommendation")
include(":domain:memory")
include(":data:mcp")
include(":data:workfiles")
include(":data:permissions")
include(":data:toolauthorization")
include(":data:skills")
include(":data:agent")
include(":data:appupdate")
include(":feature:modelsettings")
include(":feature:mcp")
include(":feature:workfiles")
include(":feature:permissions")
include(":feature:toolauthorization")
include(":feature:voicewake")
include(":feature:settings")
include(":feature:skills")
include(":feature:chat")
include(":feature:plugin")
include(":feature:recommendation")
include(":feature:memory")
include(":plugin-api")
include(":data:plugin")
include(":data:recommendation")
include(":data:memory")
include(":plugins:example")
include(":plugins:zhihu")
include(":plugins:spotify")
include(":plugins:xiaohongshu")
include(":plugins:v2ex")
