package com.gazzel.sesameapp.data.manager

import java.lang.reflect.Type // <<< ADD Import

interface ICacheManager {
    /**
     * Caches data with a specific key and type.
     * @param T The type of data being cached.
     * @param key The unique key for the cache entry.
     * @param data The data to cache.
     * @param type The reflection Type of the data (needed for Gson generics).
     * @param expiryMs The duration in milliseconds for how long the cache is valid.
     */
    suspend fun <T> cacheData(key: String, data: T, type: Type, expiryMs: Long) // <<< UPDATED Signature

    /**
     * Retrieves cached data if it exists and hasn't expired.
     * @param T The expected type of the cached data.
     * @param key The unique key for the cache entry.
     * @param type The reflection Type of the data to deserialize into.
     * @return The deserialized data of type T, or null if not found, expired, or error occurs.
     */
    suspend fun <T> getCachedData(key: String, type: Type): T? // <<< UPDATED Signature

    /**
     * Deletes all cache entries that have passed their expiry time.
     */
    suspend fun clearExpiredCache()

    /**
     * Deletes all entries from the cache.
     */
    suspend fun clearAllCache()
}