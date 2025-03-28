/**
 * Root Gradle build script for the Sesame Android app.
 *
 * This script configures project-level settings, including repositories and dependency resolution.
 * It ensures all subprojects use the same repositories and applies the dependency updates plugin
 * for checking dependency versions.
 */

plugins {
    // The dependency updates plugin
    id("com.github.ben-manes.versions") version "0.51.0"

    // Hilt & Google services "apply false" from the version catalog if you want
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}


// Configure the dependencyUpdates task to check for dependency updates
tasks.named<com.github.ben-manes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    // Check for Gradle updates
    checkForGradleUpdate = true
    // Set the output directory for the dependency updates report
    outputDir = "build/dependencyUpdates"
    // Set the name of the report file (without extension)
    reportfileName = "report"
}