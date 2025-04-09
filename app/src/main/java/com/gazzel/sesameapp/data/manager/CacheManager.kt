package com.gazzel.sesameapp.data.manager

import android.util.Log
import com.gazzel.sesameapp.data.local.CacheEntity
import com.gazzel.sesameapp.data.local.dao.CacheDao
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.Logger
import com.google.gson.Gson // <<< Import
import com.google.gson.JsonSyntaxException // <<< Import
import java.lang.reflect.Type // <<< Import
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    private val cacheDao: CacheDao, // Hilt provides this
    private val logger: Logger,     // Hilt provides this
    private val gson: Gson          // Hilt provides this
) : ICacheManager {

    // Updated method signature
    override suspend fun <T> cacheData(key: String, data: T, type: Type, expiryMs: Long) {
        if (expiryMs <= 0) {
            logger.warning("Attempted to cache data for key '$key' with non-positive expiry ($expiryMs ms). Skipping.")
            return
        }
        try {
            val serializedData: String = gson.toJson(data, type) // Use Gson
            val absoluteExpiryTime = System.currentTimeMillis() + expiryMs // Calculate expiry

            cacheDao.insert(
                CacheEntity(
                    key = key,
                    data = serializedData,
                    expiryTime = absoluteExpiryTime // Store absolute expiry
                )
            )
            logger.debug("Data cached for key: $key. Expires at: $absoluteExpiryTime (~${expiryMs/1000}s)")

        } catch (e: Exception) {
            logger.error("Failed to cache data for key: $key", AppException.DatabaseException("Cache serialization/storage failed for key '$key'", e))
        }
    }

    // Updated method signature
    override suspend fun <T> getCachedData(key: String, type: Type): T? {
        try {
            val cache: CacheEntity? = cacheDao.getCache(key)
            val currentTime = System.currentTimeMillis()

            if (cache == null) {
                logger.debug("Cache miss for key: $key")
                return null
            }

            if (cache.expiryTime <= currentTime) {
                logger.debug("Cache expired for key: $key (Expired at ${cache.expiryTime}, Now: $currentTime)")
                return null
            }

            // Cache exists and is valid, attempt deserialization
            try {
                val deserializedData: T? = gson.fromJson(cache.data, type) // Use Gson
                if (deserializedData != null) {
                    logger.debug("Cache hit for key: $key")
                    return deserializedData
                } else {
                    logger.warning("getCachedData for key '$key': Deserialized data is null.", null)
                    return null
                }
            } catch (jsonEx: JsonSyntaxException) {
                logger.error("Cache JSON syntax error for key: $key. Data: ${cache.data.take(100)}...", AppException.DatabaseException("Invalid JSON in cache for key '$key'", jsonEx))
                return null
            } catch (e: Exception) {
                logger.error("Cache deserialization error for key: $key", AppException.DatabaseException("Cache deserialization failed for key '$key'", e))
                return null
            }

        } catch (dbEx: Exception) {
            logger.error("Failed to retrieve cache from DB for key: $key", AppException.DatabaseException("Cache retrieval from DB failed for key '$key'", dbEx))
            return null
        }
    }

    override suspend fun clearExpiredCache() {
        try {
            val currentTime = System.currentTimeMillis()
            cacheDao.deleteExpired(currentTime)
            logger.info("Expired cache cleared (items older than $currentTime).")
        } catch (e: Exception) {
            logger.error("Failed to clear expired cache", AppException.DatabaseException("Failed to clear expired cache", e))
        }
    }

    override suspend fun clearAllCache() {
        try {
            cacheDao.deleteAll()
            logger.info("All cache cleared.")
        } catch (e: Exception) {
            logger.error("Failed to clear all cache", AppException.DatabaseException("Failed to clear all cache", e))
        }
    }
}