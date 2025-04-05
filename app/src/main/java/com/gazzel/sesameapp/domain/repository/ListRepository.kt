// app/src/main/java/com/gazzel/sesameapp/domain/repository/ListRepository.kt
package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow // Keep Flow if observing changes is desired

interface ListRepository { // REMOVED : BaseRepository<SesameList>

    // Changed Flow to suspend Result<List>
    suspend fun getUserLists(userId: String): Result<List<SesameList>>
    suspend fun getPublicLists(): Result<List<SesameList>>
    suspend fun getListById(id: String): Result<SesameList> // Changed return type
    suspend fun createList(list: SesameList): Result<SesameList> // Changed return type to include created list
    suspend fun updateList(list: SesameList): Result<SesameList> // Changed return type
    suspend fun deleteList(id: String): Result<Unit> // Return Result
    suspend fun getRecentLists(limit: Int = 5): Result<List<SesameList>> // Changed return type
    suspend fun searchLists(query: String): Result<List<SesameList>> // Changed return type

    // These might be better handled by PlaceRepository now, or need specific API endpoints
    suspend fun addPlaceToList(listId: String, placeId: String): Result<Unit> // Keep Result signature
    suspend fun removePlaceFromList(listId: String, placeId: String): Result<Unit> // Keep Result signature

    suspend fun followList(listId: String): Result<Unit> // Keep Result signature
    suspend fun unfollowList(listId: String): Result<Unit> // Keep Result signature

    // Removed BaseRepository methods if not needed: getById, create, update, delete (specific versions above are preferred)
    // Removed observeById as it was Firestore specific for real-time
}