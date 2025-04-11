// Add these imports at the TOP of app/build.gradle.kts
import java.util.Properties
import java.io.File
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.google.secrets)
}

// Function to safely load properties (Keep as is)
fun getApiKey(projectRootDir: File, propertyName: String): String {
    // ... (function implementation)
    val properties = Properties()
    val localPropertiesFile = File(projectRootDir, "local.properties")
    if (localPropertiesFile.isFile) {
        try {
            FileInputStream(localPropertiesFile).use { fis: FileInputStream ->
                properties.load(fis)
            }
            return properties.getProperty(propertyName)
                ?: throw RuntimeException("'$propertyName' not found in local.properties.")
        } catch (e: Exception) {
            println("Warning: Could not read local.properties: ${e.message}")
            throw RuntimeException("Failed to read '$propertyName' from local.properties.", e)
        }
    } else {
        throw RuntimeException("local.properties file not found.")
    }
}


android {
    // ... (namespace, compileSdk, defaultConfig, buildTypes, compileOptions, kotlinOptions, buildFeatures, packaging) ...
    namespace = "com.gazzel.sesameapp"
    compileSdk = 35 // Ensure this matches a valid SDK version you have installed

    defaultConfig {
        applicationId = "com.gazzel.sesameapp"
        minSdk = 24
        targetSdk = 35 // Match compileSdk or use a recent API level
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Application signing configuration can go here if needed
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        dataBinding = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // ... (ksp args) ...
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Compose Material Icons (Both core and extended)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended) // <-- ADDED THIS

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines)

    // Firebase
    implementation(libs.firebase.auth.ktx)

    // Google Play Services
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)

    // Places SDK
    implementation(libs.places)

    // Maps Compose
    implementation(libs.google.maps)

    // SQL DB
    implementation(libs.androidx.sqlite.ktx)

    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    // Logging
    implementation(libs.timber)

    // Unit Testing
    testImplementation(libs.junit) // Core JUnit
    testImplementation(libs.mockito.kotlin) // Mockito for Kotlin
    testImplementation(libs.kotlinx.coroutines.test) // Coroutine testing utilities
    testImplementation(libs.turbine) // Flow testing library
}