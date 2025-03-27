/**
 * Settings script for the Sesame Android app.
 *
 * This script configures project-wide settings, including the project name and dependency resolution
 * management. Gradle automatically uses the version catalog defined in gradle/libs.versions.toml.
 */

pluginManagement {
    repositories {
        google() // Prioritize Google's Maven repository for AGP
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

rootProject.name = "SesameAndroidApp"

include(":app")

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}