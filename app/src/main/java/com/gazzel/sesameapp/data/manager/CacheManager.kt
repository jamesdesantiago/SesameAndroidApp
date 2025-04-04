package com.gazzel.sesameapp.data.manager

import android.content.Context
import androidx.room.Room
import com.gazzel.sesameapp.data.local.AppDatabase
import com.gazzel.sesameapp.data.local.CacheEntity
import com.gazzel.sesameapp.domain.exception.AppException.DatabaseException
import com.gazzel.sesameapp.domain.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    suspend fun <T> cacheData(
        key: String,
        data: T,
        expiryTime: Long = System.currentTimeMillis() + 24 * 60 * 60 * 1000 // 24 hours
    ) {
        try {
            database.cacheDao().insert(
                CacheEntity(
                    key = key,
                    data = data.toString(),
                    expiryTime = expiryTime
                )
            )
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to cache data", e))
            throw e
        }
    }

    suspend fun <T> getCachedData(key: String): T? {
        return try {
            val cache = database.cacheDao().getCache(key)
            if (cache != null && cache.expiryTime > System.currentTimeMillis()) {
                cache.data as? T
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to get cached data", e))
            null
        }
    }

    suspend fun clearExpiredCache() {
        try {
            database.cacheDao().deleteExpired(System.currentTimeMillis())
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to clear expired cache", e))
        }
    }

    suspend fun clearAllCache() {
        try {
            database.cacheDao().deleteAll()
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to clear all cache", e))
        }
    }
} 