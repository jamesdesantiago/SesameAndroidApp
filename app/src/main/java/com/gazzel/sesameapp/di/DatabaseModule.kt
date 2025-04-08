package com.gazzel.sesameapp.di

import android.content.Context
import androidx.room.Room
import com.gazzel.sesameapp.data.local.AppDatabase
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.local.dao.CacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // Add migrations or fallback logic as needed
            .fallbackToDestructiveMigration() // Example: Use destructive migration during development
            .build()
    }

    @Provides
    @Singleton
    fun providePlaceDao(database: AppDatabase): PlaceDao {
        return database.placeDao()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    // <<< ADD Explicit provider for CacheDao >>>
    @Provides
    @Singleton
    fun provideCacheDao(database: AppDatabase): CacheDao {
        return database.cacheDao()
    }
}