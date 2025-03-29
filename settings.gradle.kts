// MINIMAL settings.gradle.kts relying on convention

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "SesameAndroidApp"
include(":app")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    // versionCatalogs block removed
}

println(">>> Minimal settings.gradle.kts (CONVENTION) executed")