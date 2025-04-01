package com.gazzel.sesameapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gazzel.sesameapp.data.local.CacheEntity


@Dao
interface CacheDao {
    @Insert
    suspend fun insert(entity: CacheEntity)

    @Query("SELECT * FROM cache WHERE `key` = :key LIMIT 1")
    suspend fun getCache(key: String): CacheEntity?

    @Query("DELETE FROM cache WHERE expiryTime < :currentTime")
    suspend fun deleteExpired(currentTime: Long)

    @Query("DELETE FROM cache")
    suspend fun deleteAll()
}
