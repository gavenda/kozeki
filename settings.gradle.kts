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
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The QuickJS binding Mihon extensions evaluate JavaScript with is only published here.
        maven("https://www.jitpack.io") {
            content { includeGroup("com.github.zhanghai.quickjs-java") }
        }
    }
}

rootProject.name = "Kozeki"
include(":app")
include(":mihon-host")
