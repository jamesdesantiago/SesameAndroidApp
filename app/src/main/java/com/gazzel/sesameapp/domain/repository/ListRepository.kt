package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.SesameList
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface ListRepository : BaseRepository<SesameList> {
    fun getUserLists(userId: String): Flow<List<SesameList>>
    fun getPublicLists(): Flow<List<SesameList>>
    suspend fun getListById(id: String): SesameList?
    suspend fun createList(list: SesameList): String
    suspend fun updateList(list: SesameList)
    suspend fun deleteList(id: String)
    suspend fun getRecentLists(limit: Int = 5): List<SesameList>
    suspend fun searchLists(query: String): List<SesameList>
    suspend fun addPlaceToList(listId: String, placeId: String): Result<Unit>
    suspend fun removePlaceFromList(listId: String, placeId: String): Result<Unit>
    suspend fun followList(listId: String): Result<Unit>
    suspend fun unfollowList(listId: String): Result<Unit>
} 