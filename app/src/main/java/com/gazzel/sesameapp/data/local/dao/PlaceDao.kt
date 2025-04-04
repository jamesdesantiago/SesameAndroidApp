package com.gazzel.sesameapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gazzel.sesameapp.data.local.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places")
    fun getAllPlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE listId = :listId")
    fun getPlacesByListId(listId: String): Flow<List<PlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<PlaceEntity>)

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    @Delete
    suspend fun deletePlace(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :placeId")
    suspend fun deletePlace(placeId: String)

    @Query("SELECT * FROM places WHERE id = :placeId LIMIT 1")
    fun getPlaceByIdFlow(placeId: String): Flow<PlaceEntity?>

    @Query("SELECT * FROM places WHERE id = :placeId LIMIT 1")
    suspend fun getPlaceById(placeId: String): PlaceEntity?
}
