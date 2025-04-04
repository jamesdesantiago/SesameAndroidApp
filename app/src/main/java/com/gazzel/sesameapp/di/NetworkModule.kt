package com.gazzel.sesameapp.di

import com.gazzel.sesameapp.data.network.SecurityInterceptor
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.remote.UserProfileService
import com.gazzel.sesameapp.data.service.ListService
import com.gazzel.sesameapp.data.service.UserListService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    // Add SecurityInterceptor as a parameter
    fun provideOkHttpClient(securityInterceptor: SecurityInterceptor): OkHttpClient { // <<< ADD parameter
        return OkHttpClient.Builder()
            // Add your custom SecurityInterceptor
            .addInterceptor(securityInterceptor) // <<< ADD this interceptor
            // Keep the Logging Interceptor
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY // Or your desired level
            })
            // Keep other configurations
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://gazzel.io/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideUserProfileService(retrofit: Retrofit): UserProfileService {
        return retrofit.create(UserProfileService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApiService(retrofit: Retrofit): UserApiService {
        return retrofit.create(UserApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserListService(retrofit: Retrofit): UserListService { // Updated to UserListService
        return retrofit.create(UserListService::class.java)
    }

    @Provides
    @Singleton
    fun provideListService(retrofit: Retrofit): ListService { // Provide ListService
        return retrofit.create(ListService::class.java) // Create using Retrofit
    }
}