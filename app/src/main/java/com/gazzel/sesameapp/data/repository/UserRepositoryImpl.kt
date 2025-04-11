// File: app/src/main/java/com/gazzel/sesameapp/data/repository/UserRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.local.entity.UserEntity // <<< Import UserEntity
import com.gazzel.sesameapp.data.manager.ICacheManager
// Import specific mappers needed
import com.gazzel.sesameapp.data.mapper.toDomain
import com.gazzel.sesameapp.data.mapper.toEntity
import com.gazzel.sesameapp.data.remote.UserApiService
// Import DTOs used in requests/responses handled here
import com.gazzel.sesameapp.data.remote.dto.UserDto // <<< Import UserDto
import com.gazzel.sesameapp.data.remote.dto.PrivacySettingsUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UserProfileUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UsernameSetDto
// Domain / Util imports
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.model.User as DomainUser // Alias Domain User
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.map // Import specific map extension for Result
import com.gazzel.sesameapp.domain.util.onError
import com.gazzel.sesameapp.domain.util.onSuccess
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userDao: UserDao,
    private val tokenProvider: TokenProvider,
    private val firebaseAuth: FirebaseAuth,
    private val cacheManager: ICacheManager
) : UserRepository {

    companion object {
        private val USER_CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1) // 1 hour
        private const val CURRENT_USER_CACHE_KEY = "current_user_entity" // Cache the entity
        private val PRIVACY_SETTINGS_TYPE: Type = PrivacySettings::class.java
        private const val PRIVACY_SETTINGS_CACHE_KEY = "privacy_settings"
        private val PRIVACY_SETTINGS_CACHE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(30)
        // Type definition for caching the UserEntity
        private val USER_ENTITY_TYPE: Type = UserEntity::class.java // Cache UserEntity
    }

    // --- Get Current User (Cache -> DB -> Network) ---
    override suspend fun getCurrentUser(): Result<DomainUser> {
        Log.d("UserRepositoryImpl", "Attempting to fetch current user...")

        // 1. Try Cache (for UserEntity)
        try {
            val cachedEntity: UserEntity? = cacheManager.getCachedData(CURRENT_USER_CACHE_KEY, USER_ENTITY_TYPE)
            if (cachedEntity != null) {
                Log.d("UserRepositoryImpl", "Cache Hit: Returning cached user entity (ID: ${cachedEntity.id})")
                return Result.success(cachedEntity.toDomain()) // Map Entity -> Domain
            }
            Log.d("UserRepositoryImpl", "Cache Miss: current user entity")
        } catch (e: Exception) { Log.e("UserRepositoryImpl", "Cache Get Error for user entity", e) }

        // 2. Try Local DB (for UserEntity)
        try {
            val localEntity: UserEntity? = withContext(Dispatchers.IO) { userDao.getCurrentUser() }
            if (localEntity != null) {
                Log.d("UserRepositoryImpl", "DB Hit: Returning user entity from DB (ID: ${localEntity.id}). Caching it.")
                try { cacheManager.cacheData(CURRENT_USER_CACHE_KEY, localEntity, USER_ENTITY_TYPE, USER_CACHE_EXPIRY_MS) }
                catch (cacheEx: Exception) { Log.e("UserRepositoryImpl", "Cache Put Error for DB user entity", cacheEx) }
                return Result.success(localEntity.toDomain()) // Map Entity -> Domain
            }
            Log.d("UserRepositoryImpl", "DB Miss: current user entity")
        } catch (e: Exception) { Log.e("UserRepositoryImpl", "DB Get Error for user entity", e) }

        // 3. Try Network (fetches UserDto)
        Log.d("UserRepositoryImpl", "Cache and DB miss. Fetching from network...")
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.getCurrentUserProfile("Bearer $token") // Fetches UserDto
            }

            if (response.isSuccessful && response.body() != null) {
                val networkUserDto = response.body()!!
                Log.d("UserRepositoryImpl", "Network fetch successful (DTO) for user ID: ${networkUserDto.id}.")
                val domainUser = networkUserDto.toDomain() // Map DTO -> Domain
                val userEntity = domainUser.toEntity() // Map Domain -> Entity

                // Save Entity to DB & Cache
                try {
                    withContext(Dispatchers.IO) { userDao.insertUser(userEntity) } // Save Entity
                    cacheManager.cacheData(CURRENT_USER_CACHE_KEY, userEntity, USER_ENTITY_TYPE, USER_CACHE_EXPIRY_MS) // Cache Entity
                    Log.d("UserRepositoryImpl", "Saved network user to DB and Cache.")
                } catch (localSaveEx: Exception) {
                    Log.e("UserRepositoryImpl", "Error saving network user locally", localSaveEx)
                    // Proceed with success, but log the error
                }
                Result.success(domainUser) // Return Domain model
            } else {
                Log.w("UserRepositoryImpl", "Network fetch failed: ${response.code()} - ${response.errorBody()?.string()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Network error fetching user", e)
            Result.error(mapExceptionToAppException(e, "Failed to fetch user data"))
        }
    }

    // --- Update Username ---
    override suspend fun updateUsername(username: String): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        Log.d("UserRepositoryImpl", "Attempting to update username to: $username")
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.setUsername(
                    authorization = "Bearer $token",
                    request = UsernameSetDto(username = username)
                )
            }
            if (response.isSuccessful) {
                Log.i("UserRepositoryImpl", "API username update successful.")
                // Update local DB and Cache on success
                try {
                    withContext(Dispatchers.IO) {
                        val currentEntity = userDao.getCurrentUser() // Get current Entity
                        if (currentEntity != null) {
                            val updatedEntity = currentEntity.copy(username = username) // Update Entity
                            userDao.insertUser(updatedEntity) // Save updated Entity
                            cacheManager.cacheData(CURRENT_USER_CACHE_KEY, updatedEntity, USER_ENTITY_TYPE, USER_CACHE_EXPIRY_MS) // Cache updated Entity
                            Log.d("UserRepositoryImpl", "Local DB and Cache updated with new username.")
                        } else {
                            invalidateUserCache() // Invalidate if no local user found
                            Log.w("UserRepositoryImpl", "Local user entity not found during username update cache.")
                        }
                    }
                } catch (localSaveEx: Exception) {
                    Log.e("UserRepositoryImpl", "Error saving updated username locally", localSaveEx)
                }
                Result.success(Unit)
            } else {
                Log.w("UserRepositoryImpl", "API username update failed: ${response.code()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception updating username", e)
            Result.error(mapExceptionToAppException(e, "Failed to update username"))
        }
    }

    // --- Check Username Status ---
    override suspend fun checkUsername(): Result<Boolean> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        Log.d("UserRepositoryImpl", "Checking username status via API...")
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.checkUsername(authorization = "Bearer $token")
            }
            if (response.isSuccessful && response.body() != null) {
                val needsUsername = response.body()!!.needsUsername
                Log.d("UserRepositoryImpl", "API check username successful: NeedsUsername=$needsUsername")
                Result.success(needsUsername)
            } else {
                Log.w("UserRepositoryImpl", "API check username failed: ${response.code()}.")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception checking username.", e)
            Result.error(mapExceptionToAppException(e, "Failed to check username status"))
        }
    }

    // --- Get Privacy Settings ---
    override suspend fun getPrivacySettings(): Result<PrivacySettings> {
        val cacheKey = PRIVACY_SETTINGS_CACHE_KEY
        // 1. Try Cache
        try {
            val cachedSettings: PrivacySettings? = cacheManager.getCachedData(cacheKey, PRIVACY_SETTINGS_TYPE)
            if (cachedSettings != null) {
                Log.d("UserRepositoryImpl", "Cache Hit: Returning cached privacy settings")
                return Result.success(cachedSettings)
            }
            Log.d("UserRepositoryImpl", "Cache Miss: privacy settings")
        } catch (e: Exception) { Log.e("UserRepositoryImpl", "Cache Get Error for privacy settings", e) }

        // 2. Try Network
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        Log.d("UserRepositoryImpl", "Fetching privacy settings from network...")
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.getPrivacySettings("Bearer $token")
            }
            if (response.isSuccessful && response.body() != null) {
                val settings = response.body()!!
                try { cacheManager.cacheData(cacheKey, settings, PRIVACY_SETTINGS_TYPE, PRIVACY_SETTINGS_CACHE_EXPIRY_MS) }
                catch (cacheEx: Exception) { Log.e("UserRepositoryImpl", "Cache Put Error for privacy settings", cacheEx) }
                Log.d("UserRepositoryImpl", "Network fetch for privacy settings successful.")
                Result.success(settings)
            } else {
                Log.w("UserRepositoryImpl", "Network fetch for privacy settings failed: ${response.code()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception fetching privacy settings", e)
            Result.error(mapExceptionToAppException(e, "Failed to get privacy settings"))
        }
    }

    // --- Update Profile (Display Name, Picture) ---
    override suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        if (displayName == null && profilePictureUrl == null) {
            Log.w("UserRepositoryImpl", "Update profile called with no changes.")
            return Result.success(Unit) // No changes to make
        }
        Log.d("UserRepositoryImpl", "Attempting to update profile...")
        return try {
            val updateDto = UserProfileUpdateDto(displayName = displayName, profilePicture = profilePictureUrl)
            val response = withContext(Dispatchers.IO) {
                userApiService.updateUserProfile("Bearer $token", updateDto) // Returns UserDto
            }
            if (response.isSuccessful && response.body() != null) {
                val updatedNetworkUserDto = response.body()!!
                Log.i("UserRepositoryImpl", "API profile update successful, updating cache and DB.")
                val domainUser = updatedNetworkUserDto.toDomain() // Map DTO -> Domain
                val userEntity = domainUser.toEntity() // Map Domain -> Entity
                try {
                    withContext(Dispatchers.IO) { userDao.insertUser(userEntity) } // Save Entity
                    cacheManager.cacheData(CURRENT_USER_CACHE_KEY, userEntity, USER_ENTITY_TYPE, USER_CACHE_EXPIRY_MS) // Cache Entity
                    Log.d("UserRepositoryImpl", "Local DB and Cache updated with new profile info.")
                } catch (localSaveEx: Exception) {
                    Log.e("UserRepositoryImpl", "Error saving updated profile locally", localSaveEx)
                }
                Result.success(Unit)
            } else {
                Log.w("UserRepositoryImpl", "API profile update failed: ${response.code()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception updating profile", e)
            Result.error(mapExceptionToAppException(e, "Failed to update profile"))
        }
    }

    // --- Update Privacy Setting Helper ---
    private suspend fun updatePrivacySetting(updateDto: PrivacySettingsUpdateDto): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        Log.d("UserRepositoryImpl", "Updating privacy setting via API...")
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.updatePrivacySettings("Bearer $token", updateDto)
            }
            if (response.isSuccessful) {
                try { // Invalidate cache on success
                    cacheManager.cacheData<PrivacySettings?>(PRIVACY_SETTINGS_CACHE_KEY, null, PRIVACY_SETTINGS_TYPE, -1L)
                    Log.d("UserRepositoryImpl", "API privacy update successful & cache invalidated.")
                } catch (cacheEx: Exception) { Log.e("UserRepositoryImpl", "Cache Invalidation Error for privacy settings", cacheEx) }
                Result.success(Unit)
            } else {
                Log.w("UserRepositoryImpl", "API privacy update failed: ${response.code()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception updating privacy setting", e)
            Result.error(mapExceptionToAppException(e, "Failed to update privacy setting"))
        }
    }
    // Specific privacy update methods calling the helper
    override suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(profileIsPublic = isPublic))
    override suspend fun updateListVisibility(arePublic: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(listsArePublic = arePublic))
    override suspend fun updateAnalytics(enabled: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(allowAnalytics = enabled))


    // --- Sign Out ---
    override suspend fun signOut(): Result<Unit> {
        return try {
            Log.i("UserRepositoryImpl", "Signing out user...")
            // Clear Cache first
            try {
                cacheManager.clearAllCache()
                Log.d("UserRepositoryImpl", "Cache cleared successfully.")
            } catch (cacheEx: Exception) { Log.e("UserRepositoryImpl", "Failed to clear cache during sign out", cacheEx) }
            // Sign out from Firebase
            withContext(Dispatchers.Default) { firebaseAuth.signOut() }
            // Clear local DB
            withContext(Dispatchers.IO) { userDao.deleteAllUsers() }
            // Clear token provider
            tokenProvider.clearToken()
            Log.i("UserRepositoryImpl", "Sign out complete.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Sign out failed", e)
            Result.error(mapExceptionToAppException(e, "Sign out failed"))
        }
    }

    // --- Delete Account ---
    override suspend fun deleteAccount(): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        Log.i("UserRepositoryImpl", "Attempting to delete account via API...")
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.deleteAccount("Bearer $token")
            }
            if (response.isSuccessful || response.code() == 204) { // 204 No Content is also success
                Log.i("UserRepositoryImpl", "API account deletion successful. Clearing local data via signOut.")
                signOut() // Sign out handles clearing all local state
                Result.success(Unit)
            } else {
                Log.w("UserRepositoryImpl", "API account deletion failed: ${response.code()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception deleting account", e)
            Result.error(mapExceptionToAppException(e, "Failed to delete account"))
        }
    }

    // --- Helper to invalidate user cache specifically ---
    private suspend fun invalidateUserCache() {
        try {
            // Setting data to null with immediate expiry effectively deletes it
            cacheManager.cacheData<UserEntity?>(CURRENT_USER_CACHE_KEY, null, USER_ENTITY_TYPE, -1L)
            Log.d("UserRepositoryImpl", "User cache invalidated.")
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Failed to invalidate user cache", e)
        }
    }

    // --- Error Mapping Helpers (Keep as they are, or refine) ---
    private fun mapApiError(code: Int, errorBody: String?): AppException {
        val defaultMsg = "API Error $code"
        val bodyMsg = errorBody ?: "No message"
        Log.e("UserRepositoryImpl", "$defaultMsg: $bodyMsg")
        return when (code) {
            401 -> AppException.AuthException("Authentication failed. Please sign in again.")
            403 -> AppException.AuthException("Permission denied.")
            404 -> AppException.ResourceNotFoundException("User or resource not found.")
            409 -> AppException.ValidationException(errorBody ?: "Conflict detected (e.g., username taken).") // More specific message
            422 -> AppException.ValidationException(errorBody ?: "Invalid data submitted.")
            in 500..599 -> AppException.NetworkException("Server error ($code). Please try again later.", code)
            else -> AppException.NetworkException("Network error ($code): $bodyMsg", code)
        }
    }

    private fun mapExceptionToAppException(e: Throwable, defaultMessage: String): AppException {
        Log.e("UserRepositoryImpl", "$defaultMessage: ${e.message}", e)
        return when (e) {
            is retrofit2.HttpException -> mapApiError(e.code(), e.response()?.errorBody()?.string())
            is IOException -> AppException.NetworkException("Network connection issue. Please check your connection.", cause = e)
            is AppException -> e // Don't re-wrap existing AppExceptions
            else -> AppException.UnknownException(e.message ?: defaultMessage, e)
        }
    }
}