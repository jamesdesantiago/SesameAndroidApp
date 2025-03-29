/**
 * Root Gradle build script for the Sesame Android app.
 *
 * This script configures project-level settings, including repositories and dependency resolution.
 * It ensures all subprojects use the same repositories and applies the dependency updates plugin
 * for checking dependency versions.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    // The dependency updates plugin
    id("com.github.ben-manes.versions") version "0.51.0"

    // Hilt & Google services "apply false" from the version catalog if you want
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

// Configure the dependencyUpdates task to check for dependency updates
// Configure the dependencyUpdates task to check for dependency updates
tasks.named("dependencyUpdates") {
    // Cast 'this' to the specific task type
    val task = this as DependencyUpdatesTask // <-- ADD THIS CAST

    // Check for Gradle updates
    task.checkForGradleUpdate = true // <-- Use 'task.' prefix
    // Set the output directory for the dependency updates report
    task.outputDir = "build/dependencyUpdates" // <-- Use 'task.' prefix
    // Set the name of the report file (without extension)
    task.reportfileName = "report" // <-- Use 'task.' prefix
}