// File: app/src/main/java/com/gazzel/sesameapp/data/local/AppDatabase.kt
package com.gazzel.sesameapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gazzel.sesameapp.data.local.dao.CacheDao
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity // <<< KEEP
import com.gazzel.sesameapp.data.local.entity.UserEntity // <<< CHANGE

@Database(
    entities = [
        PlaceEntity::class,
        UserEntity::class, // <<< CHANGE to UserEntity
        CacheEntity::class
    ],
    version = 1, // <<< Increment version if schema changed (e.g., UserEntity structure differs from old User)
    exportSchema = false // Keep false unless you export schemas
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun userDao(): UserDao
    abstract fun cacheDao(): CacheDao

    companion object {
        const val DATABASE_NAME = "sesame_db"
    }
}
// IMPORTANT: If the structure of UserEntity is different from the old data/model/User,
// you MUST increment the database version and provide a Migration or use fallbackToDestructiveMigration().
// Since we primarily renamed and moved it, destructive migration during development (as configured in DatabaseModule) is likely fine.