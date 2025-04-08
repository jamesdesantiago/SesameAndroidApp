// File: app/src/main/java/com/gazzel/sesameapp/di/NetworkModule.kt
package com.gazzel.sesameapp.di

// --- Consolidated & Current Services ---
import com.gazzel.sesameapp.data.service.ListApiService // The new consolidated service
import com.gazzel.sesameapp.data.remote.UserApiService // Keep for now, will consolidate User services next
import com.gazzel.sesameapp.data.service.GooglePlacesService // For Google Places

// --- Hilt, OkHttp, Retrofit ---
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

// --- Optional: Security Interceptor ---
// import com.gazzel.sesameapp.data.network.SecurityInterceptor // Uncomment if you have this and want to use it

// Define qualifier names as constants (can be in a separate file or here)
const val GAZEL_RETROFIT = "GazelRetrofit"
const val GOOGLE_PLACES_RETROFIT = "GooglePlacesRetrofit"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        // Inject SecurityInterceptor if you plan to use it
        // securityInterceptor: SecurityInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                // Use BODY for development/debugging, change to NONE for release builds
                level = HttpLoggingInterceptor.Level.BODY
            })
            // Uncomment the line below to add the security interceptor
            // .addInterceptor(securityInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS) // Reasonable timeouts
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides the Retrofit instance configured for your Gazel backend API.
     */
    @Provides
    @Singleton
    @Named(GAZEL_RETROFIT) // Qualifier for your backend
    fun provideGazelRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            // Replace with your actual backend base URL
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            // Using Gson, ensure your DTOs work well with it (or use Moshi/Kotlinx.Serialization)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provides the Retrofit instance configured for the Google Places API.
     */
    @Provides
    @Singleton
    @Named(GOOGLE_PLACES_RETROFIT) // Qualifier for Google Places
    fun provideGooglePlacesRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            // Standard Google Places API base URL
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient) // Can reuse the same OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // --- API Service Providers ---

    /**
     * Provides the consolidated ListApiService using the Gazel backend Retrofit instance.
     */
    @Provides
    @Singleton
    fun provideListApiService(@Named(GAZEL_RETROFIT) retrofit: Retrofit): ListApiService {
        return retrofit.create(ListApiService::class.java)
    }

    /**
     * Provides the UserApiService using the Gazel backend Retrofit instance.
     * Note: This interface might also need consolidation later.
     */
    @Provides
    @Singleton
    fun provideUserApiService(@Named(GAZEL_RETROFIT) retrofit: Retrofit): UserApiService {
        return retrofit.create(UserApiService::class.java)
    }

    /**
     * Provides the GooglePlacesService using the Google Places Retrofit instance.
     */
    @Provides
    @Singleton
    fun provideGooglePlacesService(@Named(GOOGLE_PLACES_RETROFIT) retrofit: Retrofit): GooglePlacesService {
        return retrofit.create(GooglePlacesService::class.java)
    }
}