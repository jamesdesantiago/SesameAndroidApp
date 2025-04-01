plugins {
    alias(libs.plugins.android.application) // Apply AGP
    alias(libs.plugins.kotlin.android)    // Apply Kotlin for Android
    alias(libs.plugins.kotlin.compose)    // Apply Kotlin Compose Plugin
    //alias(libs.plugins.kotlin.kapt)       // Apply Kapt (for Hilt)
    alias(libs.plugins.ksp)               // Apply KSP (for Room)
    alias(libs.plugins.hilt)               // Apply Hilt
    alias(libs.plugins.google.services)    // Apply GMS
}

android {
    namespace = "com.gazzel.sesameapp" // Your original namespace
    compileSdk = 35 // From working project

    defaultConfig {
        applicationId = "com.gazzel.sesameapp"
        minSdk = 24 // Your original minSdk
        targetSdk = 35 // From working project
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Your original setting
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Use 17 for Kotlin 2.0 / AGP 8.x compatibility
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        // buildFeatures.compose = true // Not needed when using kotlin.compose plugin
    }
    // composeOptions { } // Not needed when using kotlin.compose plugin
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp { // Add KSP config back
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

    // implementation(libs.okhttp.logging) // Add OkHttp logging if needed
    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Example: implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coroutines)

    // Example: implementation(platform(libs.firebase.bom))
    // Example: implementation(libs.firebase.auth)

    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.places)
    implementation(libs.google.maps)
}