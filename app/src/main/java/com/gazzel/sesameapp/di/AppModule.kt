package com.gazzel.sesameapp.di

import android.content.Context
import androidx.room.Room
import com.gazzel.sesameapp.data.local.AppDatabase
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.repository.UserRepositoryImpl
import com.gazzel.sesameapp.domain.repository.UserRepository
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
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).build()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase) = database.userDao()

    @Provides
    @Singleton
    fun provideUserRepository(
        userApiService: UserApiService,
        userDao: UserDao
    ): UserRepository {
        return UserRepositoryImpl(userApiService, userDao)
    }
}