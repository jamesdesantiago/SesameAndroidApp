// domain/repository/UserRepository.kt
package com.gazzel.sesameapp.domain.repository

import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.model.User
import com.gazzel.sesameapp.domain.util.Result
import kotlinx.coroutines.flow.Flow // Keep Flow only if you intend to observe DB changes later

interface UserRepository {
    // --- Method Signature Changes ---
    suspend fun getCurrentUser(): Result<User> // Changed from Flow<User>
    suspend fun updateUsername(username: String): Result<Unit> // Keep
    suspend fun checkUsername(): Result<Boolean> // Changed from Flow<Boolean>
    suspend fun getPrivacySettings(): Result<PrivacySettings> // Changed from PrivacySettings
    // --- End Changes ---

    suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit> // Keep
    suspend fun updateListVisibility(arePublic: Boolean): Result<Unit> // Keep
    suspend fun updateAnalytics(enabled: Boolean): Result<Unit> // Keep
    suspend fun deleteAccount(): Result<Unit> // Keep
    suspend fun signOut(): Result<Unit> // Keep
    suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit> // Keep
}