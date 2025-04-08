package com.gazzel.sesameapp.data.manager

// Imports NO LONGER needed:
// import android.content.Context
// import androidx.room.Room
// import com.gazzel.sesameapp.data.local.AppDatabase
// import dagger.hilt.android.qualifiers.ApplicationContext

// Keep these imports:
import com.gazzel.sesameapp.data.local.dao.CacheDao // Keep DAO import
import com.gazzel.sesameapp.data.local.CacheEntity
import com.gazzel.sesameapp.data.manager.ICacheManager // Keep Interface import
import com.gazzel.sesameapp.domain.exception.AppException.DatabaseException // Or use a specific CacheException
import com.gazzel.sesameapp.domain.util.Logger
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log // Using Log directly or keep Logger

// TODO: Import necessary serialization library (e.g., Gson, Moshi, Kotlinx Serialization)
// import com.google.gson.Gson // Example for Gson

@Singleton
class CacheManager @Inject constructor(
    private val cacheDao: CacheDao, // Inject DAO
    private val logger: Logger
    // private val gson: Gson // Example: Inject Gson for serialization
) : ICacheManager {

    // <<< REMOVE the manual database creation >>>
    /*
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context, // context is no longer available here
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    */

    // Default expiry time constant (optional)
    companion object {
        private const val DEFAULT_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    override suspend fun <T> cacheData(
        key: String,
        data: T,
        expiryTime: Long // Removed default here, caller provides or uses constant
    ) {
        try {
            // TODO: Implement proper serialization for 'data' based on type T
            val serializedData: String = data.toString() // Placeholder - NEEDS REPLACEMENT
            // Example using Gson (if injected):
            // val serializedData = gson.toJson(data)

            if (serializedData == null) {
                logger.error(DatabaseException("Failed to serialize data for cache key: $key", null))
                return // Don't insert null data
            }

            // Use the injected cacheDao
            cacheDao.insert( // <<< FIX: Use injected cacheDao directly
                CacheEntity(
                    key = key,
                    data = serializedData, // Use the serialized string
                    expiryTime = expiryTime
                )
            )
            logger.debug("Data cached for key: $key")

        } catch (e: Exception) {
            // Log using DatabaseException or a new CacheException type
            logger.error(DatabaseException("Failed to cache data for key: $key", e))
            // Decide if you want to re-throw or just log
            // throw DatabaseException("Failed to cache data for key: $key", e) // Optional re-throw
        }
    }

    // Override annotation was missing
    override suspend fun <T> getCachedData(key: String): T? { // <<< ADD override
        return try {
            // Use the injected cacheDao
            val cache: CacheEntity? = cacheDao.getCache(key) // <<< FIX: Use injected cacheDao
            if (cache != null && cache.expiryTime > System.currentTimeMillis()) {
                // TODO: Implement proper deserialization for 'cache.data' based on type T
                // Example using Gson (if injected and T's class is known/passed):
                // return gson.fromJson(cache.data, object : TypeToken<T>() {}.type) // Needs type info

                logger.warning("getCachedData for key '$key': Deserialization needed. Returning null.")
                null // <<< Needs proper JSON deserialization
            } else {
                if (cache != null) {
                    logger.debug("Cache expired for key: $key")
                    // Optionally delete the expired item here proactively
                    // cacheDao.deleteExpiredForKey(key, System.currentTimeMillis()) // Need DAO method for this
                } else {
                    logger.debug("Cache miss for key: $key")
                }
                null
            }
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to get cached data for key: $key", e))
            null // Return null on error
        }
    }

    // Override annotation was missing
    override suspend fun clearExpiredCache() { // <<< ADD override
        try {
            // Use the injected cacheDao
            cacheDao.deleteExpired(System.currentTimeMillis()) // <<< FIX: Use injected cacheDao
            logger.info("Expired cache cleared.")
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to clear expired cache", e))
        }
    }

    // Override annotation was missing
    override suspend fun clearAllCache() { // <<< ADD override
        try {
            // Use the injected cacheDao
            cacheDao.deleteAll() // <<< FIX: Use injected cacheDao
            logger.info("All cache cleared.")
        } catch (e: Exception) {
            logger.error(DatabaseException("Failed to clear all cache", e))
        }
    }
}