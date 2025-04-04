package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUser(): Flow<User>
    suspend fun updateUsername(username: String): Result<Unit>
    suspend fun checkUsername(): Flow<Boolean>
    suspend fun getPrivacySettings(): PrivacySettings
    suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit>
    suspend fun updateListVisibility(arePublic: Boolean): Result<Unit>
    suspend fun updateAnalytics(enabled: Boolean): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun signOut(): Result<Unit> // Use Result for potential errors
    suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit>

}