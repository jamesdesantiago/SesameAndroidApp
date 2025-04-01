package com.gazzel.sesameapp.di

import com.gazzel.sesameapp.data.repository.LocationRepositoryImpl
import com.gazzel.sesameapp.data.repository.PlaceRepositoryImpl
import com.gazzel.sesameapp.data.repository.HelpRepositoryImpl
import com.gazzel.sesameapp.data.repository.NotificationRepositoryImpl
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import com.gazzel.sesameapp.domain.repository.HelpRepository
import com.gazzel.sesameapp.domain.repository.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindPlaceRepository(
        placeRepositoryImpl: PlaceRepositoryImpl
    ): PlaceRepository

    companion object {
        @Provides
        @Singleton
        fun provideHelpRepository(): HelpRepository = HelpRepositoryImpl()
    }

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        notificationRepositoryImpl: NotificationRepositoryImpl
    ): NotificationRepository
}
