/**
 * Root Gradle build script for the Sesame Android app.
 *
 * This script configures project-level settings, including repositories and dependency resolution.
 * It ensures all subprojects use the same repositories and applies the dependency updates plugin
 * for checking dependency versions.
 */

plugins {
    // Apply the dependency updates plugin at the project level
    id("com.github.ben-manes.versions") version "0.51.0"
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Configure the dependencyUpdates task to check for dependency updates
tasks.named<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask>("dependencyUpdates") {
    // Check for Gradle updates
    checkForGradleUpdate = true
    // Set the output directory for the dependency updates report (as a String)
    outputDir = "build/dependencyUpdates"
    // Set the name of the report file (without extension)
    reportfileName = "report"
}