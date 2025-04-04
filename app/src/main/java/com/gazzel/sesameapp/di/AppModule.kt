package com.gazzel.sesameapp.di

import android.content.Context
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.repository.UserRepositoryImpl
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        userApiService: UserApiService,
        userDao: UserDao,
        firebaseAuth: FirebaseAuth
    ): UserRepository {
        return UserRepositoryImpl(userApiService, userDao, firebaseAuth)
    }

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context // Inject the application context
    ): FusedLocationProviderClient {
        // Use the standard LocationServices factory method
        return LocationServices.getFusedLocationProviderClient(context)
    }
}