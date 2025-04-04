// Add these imports at the TOP of app/build.gradle.kts
import java.util.Properties
import java.io.File // <-- Import java.io.File
import java.io.FileInputStream // <-- Import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Function to safely load properties
// Use the imported File type directly now
fun getApiKey(projectRootDir: File, propertyName: String): String {
    val properties = Properties()
    val localPropertiesFile = File(projectRootDir, "local.properties") // Use imported File
    if (localPropertiesFile.isFile) {
        try {
            // Add explicit type 'FileInputStream' for 'fis' in the lambda (helps compiler)
            FileInputStream(localPropertiesFile).use { fis: FileInputStream ->
                properties.load(fis) // Now compiler knows 'fis' is an InputStream
            }
            return properties.getProperty(propertyName)
                ?: throw RuntimeException("'$propertyName' not found in local.properties. Make sure it's defined.")
        } catch (e: Exception) {
            println("Warning: Could not read local.properties: ${e.message}")
            throw RuntimeException("Failed to read '$propertyName' from local.properties. Make sure the file exists and the property is set.", e)
        }
    } else {
        throw RuntimeException("local.properties file not found in root project directory. Please create it and add '$propertyName'.")
    }
}


android {
    namespace = "com.gazzel.sesameapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gazzel.sesameapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Read the API key from local.properties
        val mapsApiKey = getApiKey(rootProject.rootDir, "MAPS_API_KEY")

        // Make the API key available in BuildConfig
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")

        // Make the API key available as a manifest placeholder
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // You can reuse the key loading logic here if needed for release-specific keys
            // val mapsApiKeyRelease = getApiKey(rootProject.rootDir, "MAPS_API_KEY_RELEASE") // Example
            // buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKeyRelease\"")
            // manifestPlaceholders["mapsApiKey"] = mapsApiKeyRelease
        }
        debug {
            // If you have specific debug keys, load them here
            // val mapsApiKeyDebug = getApiKey(rootProject.rootDir, "MAPS_API_KEY_DEBUG") // Example
            // buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKeyDebug\"")
            // manifestPlaceholders["mapsApiKey"] = mapsApiKeyDebug
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // Add ALL dependencies from your problematic project back here,
    // ensuring they use the updated versions from libs.versions.toml where applicable
    implementation(platform(libs.androidx.compose.bom)) // Use the newer BOM

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)
    implementation(libs.compose.material.icons.core)

    // Compose (BOM handles versions)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Hilt (Use updated versions)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler) // Use ksp for Hilt compiler
    implementation(libs.androidx.hilt.navigation.compose)

    // Room (Use KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // Use ksp for Room compiler

    // Retrofit (Use updated versions)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging) // Ensure you have this alias in libs.versions.toml

    // Coroutines
    implementation(libs.coroutines) // Ensure you have this alias

    // Firebase
    // implementation(platform(libs.firebase.bom)) // Uncomment if using Firebase BOM
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)

    // Google Play Services
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth) // For Google Sign-In (legacy, maybe remove if only using Identity)
    implementation(libs.play.services.identity) // <-- ADD THIS for the newer Google Sign-In (One Tap)

    // Places
    implementation(libs.places) // The Places SDK itself

    // Maps Compose
    implementation(libs.google.maps) // Assuming this is your alias for maps-compose

    // Testing - Add if needed
    // testImplementation(libs.junit)
    // androidTestImplementation(libs.androidx.test.ext.junit)
    // androidTestImplementation(libs.androidx.test.espresso.core)
    // androidTestImplementation(platform(libs.androidx.compose.bom))
    // androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // debugImplementation(libs.androidx.compose.ui.tooling)
    // debugImplementation(libs.androidx.compose.ui.test.manifest)
}