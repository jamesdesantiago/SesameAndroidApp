package com.gazzel.sesameapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gazzel.sesameapp.data.local.dao.PlaceDao
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import com.gazzel.sesameapp.data.model.User

@Database(
    entities = [PlaceEntity::class, User::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "sesame_db"
    }
}
