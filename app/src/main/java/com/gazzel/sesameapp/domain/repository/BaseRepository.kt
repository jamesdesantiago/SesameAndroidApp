package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface BaseRepository<T> {
    suspend fun getById(id: String): Result<T>
    suspend fun create(item: T): Result<String>
    suspend fun update(item: T): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
    fun observeById(id: String): Flow<T?>
} 