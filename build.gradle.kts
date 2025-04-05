import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    id("com.github.ben-manes.versions") version "0.51.0"
    // Centrally manage versions of plugins used in modules:
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false // Add if app uses it
    alias(libs.plugins.ksp) apply false            // Add for Room
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

tasks.named("dependencyUpdates") {
    val task = this as DependencyUpdatesTask
    task.checkForGradleUpdate = true
    task.outputDir = "build/dependencyUpdates"
    task.reportfileName = "report"
}

println(">>> Root Build V_ApplyFalse executed")