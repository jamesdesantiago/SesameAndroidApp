// app/src/main/java/com/gazzel/sesameapp/di/NetworkModule.kt
package com.gazzel.sesameapp.di

import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.service.AppListService // Import the new service
import com.gazzel.sesameapp.data.service.GooglePlacesService // Keep if used
import com.gazzel.sesameapp.data.service.UserProfileService
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

// Define qualifier names as constants
const val GAZEL_RETROFIT = "GazelRetrofit"
const val GOOGLE_PLACES_RETROFIT = "GooglePlacesRetrofit"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // Provide Retrofit instance for your backend API
    @Provides
    @Singleton
    @Named(GAZEL_RETROFIT) // <-- Add qualifier
    fun provideGazelRetrofit(okHttpClient: OkHttpClient): Retrofit { // Renamed for clarity
        return Retrofit.Builder()
            .baseUrl("https://gazzel.io/") // Your FastAPI base URL
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Provide Retrofit instance for Google Places API
    @Provides
    @Singleton
    @Named(GOOGLE_PLACES_RETROFIT) // <-- Add qualifier
    fun provideGooglePlacesRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/") // Google Places base URL
            .client(okHttpClient) // Can reuse OkHttpClient
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideUserApiService(@Named(GAZEL_RETROFIT) retrofit: Retrofit): UserApiService {
        return retrofit.create(UserApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAppListService(@Named(GAZEL_RETROFIT) retrofit: Retrofit): AppListService {
        return retrofit.create(AppListService::class.java)
    }

    @Provides
    @Singleton
    fun provideGooglePlacesService(@Named(GOOGLE_PLACES_RETROFIT) retrofit: Retrofit): GooglePlacesService {
        return retrofit.create(GooglePlacesService::class.java)
    }

    // ADDED: Provider for UserProfileService
    @Provides
    @Singleton
    fun provideUserProfileService(@Named(GAZEL_RETROFIT) retrofit: Retrofit): UserProfileService {
        // Use the Retrofit instance configured for your gazel.io backend
        return retrofit.create(UserProfileService::class.java)
    }

    // REMOVED providers for the old ListService and UserListService if they were separate
}