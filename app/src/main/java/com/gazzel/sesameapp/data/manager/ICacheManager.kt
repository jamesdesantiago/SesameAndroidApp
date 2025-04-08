package com.gazzel.sesameapp.data.manager

// Interface defining the contract for CacheManager
interface ICacheManager {
    suspend fun <T> cacheData(key: String, data: T, expiryTime: Long)
    suspend fun <T> getCachedData(key: String): T?
    suspend fun clearExpiredCache()
    suspend fun clearAllCache()
}