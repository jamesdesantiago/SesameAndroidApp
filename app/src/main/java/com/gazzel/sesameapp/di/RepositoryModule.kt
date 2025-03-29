package com.gazzel.sesameapp.di

import com.gazzel.sesameapp.data.repository.LocationRepositoryImpl
import com.gazzel.sesameapp.data.repository.PlaceRepositoryImpl
import com.gazzel.sesameapp.domain.repository.LocationRepository
import com.gazzel.sesameapp.domain.repository.PlaceRepository
import dagger.Binds
import dagger.Module
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
} 