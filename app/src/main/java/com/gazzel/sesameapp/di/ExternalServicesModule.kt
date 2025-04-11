package com.gazzel.sesameapp.di

import android.content.Context
import com.gazzel.sesameapp.BuildConfig
import com.gazzel.sesameapp.R
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExternalServicesModule {

    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient {
        // Initialize Places if not already done
        if (!Places.isInitialized()) {
            // Use the key from BuildConfig
            Places.initialize(context)
        }
        return Places.createClient(context)
    }
}