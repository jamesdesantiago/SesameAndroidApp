// app/src/main/java/com/gazzel/sesameapp/data/local/dao/UserDao.kt
package com.gazzel.sesameapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
// Import the new UserEntity
import com.gazzel.sesameapp.data.local.entity.UserEntity // <<< CHANGED import

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity? // <<< CHANGED return type

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity) // <<< CHANGED parameter type

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}