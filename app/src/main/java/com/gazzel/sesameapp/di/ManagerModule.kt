package com.gazzel.sesameapp.di

// Import CacheManager and its interface
import com.gazzel.sesameapp.data.manager.CacheManager
import com.gazzel.sesameapp.data.manager.ICacheManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManagerModule {

    @Binds
    @Singleton
    abstract fun bindCacheManager(
        impl: CacheManager // Hilt injects CacheManager using its @Inject constructor
    ): ICacheManager // Bind implementation to the interface
}