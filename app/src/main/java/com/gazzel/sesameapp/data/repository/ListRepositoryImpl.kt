// data/repository/UserRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.remote.dto.UsernameSetDto // Corrected import name
import com.gazzel.sesameapp.data.remote.dto.UserProfileUpdateDto
import com.gazzel.sesameapp.data.remote.dto.PrivacySettingsUpdateDto
import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.model.User as DomainUser
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.data.mapper.toDomain // Import mapper
import com.gazzel.sesameapp.data.model.User as DataUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.auth.FirebaseAuth
import com.gazzel.sesameapp.data.manager.ICacheManager // <<< Import ICacheManager
import com.google.gson.reflect.TypeToken // <<< Import TypeToken
import java.lang.reflect.Type // <<< Import Type
import java.util.concurrent.TimeUnit // <<< For expiry


@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userDao: UserDao,
    private val tokenProvider: TokenProvider,
    private val firebaseAuth: FirebaseAuth,
    private val cacheManager: ICacheManager // <<< Inject CacheManager
) : UserRepository {

    // Cache constants
    companion object {
        private val USER_CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1) // 1 hour
        private const val CURRENT_USER_CACHE_KEY = "current_user"
        // Add other keys as needed (e.g., privacy settings)
    }

    // --- Change getCurrentUser from Flow to suspend fun ---
    override suspend fun getCurrentUser(): Result<DomainUser> {
        Log.d("UserRepositoryImpl", "Attempting to fetch current user...")
        val userType: Type = DataUser::class.java // Type for single user

        // 1. Try Cache (using the specific key)
        try {
            val cachedUser = cacheManager.getCachedData<DataUser>(CURRENT_USER_CACHE_KEY, userType)
            if (cachedUser != null) {
                Log.d("UserRepositoryImpl", "Cache Hit: Returning cached current user")
                return Result.success(cachedUser.toDomain()) // Map DataUser -> DomainUser
            }
            Log.d("UserRepositoryImpl", "Cache Miss: current user")
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Cache Get Error for current user", e)
        }


        // 2. Try Local DB (fallback if cache fails or misses)
        try {
            val localUser = withContext(Dispatchers.IO) { userDao.getCurrentUser() }
            if (localUser != null) {
                Log.d("UserRepositoryImpl", "DB Hit: Returning user from local DB.")
                // Optionally cache the DB result before returning
                cacheManager.cacheData(CURRENT_USER_CACHE_KEY, localUser, userType, USER_CACHE_EXPIRY_MS)
                return Result.success(localUser.toDomain())
            }
            Log.d("UserRepositoryImpl", "DB Miss: current user")
        } catch(e: Exception) {
            Log.e("UserRepositoryImpl", "DB Get Error for current user", e)
        }

        // 3. Try Network (last resort)
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("UserRepositoryImpl", "No token, cannot fetch user from network.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.getCurrentUserProfile("Bearer $token")
            }
            if (response.isSuccessful && response.body() != null) {
                val networkUser = response.body()!! // This is DataUser
                Log.d("UserRepositoryImpl", "Network fetch successful.")
                // Save to DB
                withContext(Dispatchers.IO) { userDao.insertUser(networkUser) }
                // Save to Cache
                cacheManager.cacheData(CURRENT_USER_CACHE_KEY, networkUser, userType, USER_CACHE_EXPIRY_MS)
                Result.success(networkUser.toDomain()) // Return mapped DomainUser
            } else {
                Log.w("UserRepositoryImpl", "Network fetch failed: ${response.code()}")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Network error fetching user", e)
            Result.error(mapExceptionToAppException(e, "Failed to fetch user data"))
        }
    }


    override suspend fun updateUsername(username: String): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))

        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.setUsername(
                    authorization = "Bearer $token",
                    request = UsernameSetDto(username = username) // Use correct DTO
                )
            }

            if (response.isSuccessful) {
                // Update local DB and Cache on success
                withContext(Dispatchers.IO) {
                    val localUser = userDao.getCurrentUser()
                    if (localUser != null) {
                        val updatedUser = localUser.copy(username = username)
                        userDao.insertUser(updatedUser)
                        // Update cache
                        val userType: Type = DataUser::class.java
                        cacheManager.cacheData(CURRENT_USER_CACHE_KEY, updatedUser, userType, USER_CACHE_EXPIRY_MS)
                        Log.d("UserRepositoryImpl", "Local DB and Cache updated with new username.")
                    } else {
                        Log.w("UserRepositoryImpl", "Local user not found during username update cache.")
                        // Invalidate cache if user not found locally?
                        cacheManager.cacheData<DataUser?>(CURRENT_USER_CACHE_KEY, null, userType, -1L)
                    }
                }
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to update username"))
        }
    }

    // --- Change checkUsername from Flow to suspend fun ---
    override suspend fun checkUsername(): Result<Boolean> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))

        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.checkUsername(authorization = "Bearer $token")
            }

            if (response.isSuccessful && response.body() != null) {
                val needsUsername = response.body()!!.needsUsername
                Log.d("UserRepositoryImpl", "API check username successful: NeedsUsername=$needsUsername")
                Result.success(needsUsername)
            } else {
                Log.e("UserRepositoryImpl", "API check username failed: ${response.code()}.")
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception checking username.", e)
            Result.error(mapExceptionToAppException(e, "Failed to check username status"))
        }
    }

    // --- Change getPrivacySettings return type ---
    override suspend fun getPrivacySettings(): Result<PrivacySettings> { // <<< Changed return type
        // TODO: Implement Caching for Privacy Settings if desired
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))

        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.getPrivacySettings("Bearer $token")
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to get privacy settings"))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            Log.d("UserRepositoryImpl", "Signing out user...")
            // Clear cache first
            try {
                cacheManager.clearAllCache() // Or at least clear user-specific keys
                Log.d("UserRepositoryImpl", "User cache cleared.")
            } catch (cacheEx: Exception) {
                Log.e("UserRepositoryImpl", "Failed to clear cache during sign out", cacheEx)
            }
            // Sign out Firebase
            withContext(Dispatchers.Default) {
                firebaseAuth.signOut()
            }
            // Clear local DB
            withContext(Dispatchers.IO) {
                userDao.deleteAllUsers()
            }
            // Clear token provider cache
            tokenProvider.clearToken()
            Log.d("UserRepositoryImpl", "Sign out successful.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Sign out failed", e)
            Result.error(mapExceptionToAppException(e, "Sign out failed"))
        }
    }

    override suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        if (displayName == null && profilePictureUrl == null) return Result.success(Unit) // Nothing to update

        return try {
            val updateDto = UserProfileUpdateDto(displayName = displayName, profilePicture = profilePictureUrl)
            val response = withContext(Dispatchers.IO) {
                userApiService.updateUserProfile("Bearer $token", updateDto) // Call actual API
            }

            if (response.isSuccessful && response.body() != null) {
                val updatedNetworkUser = response.body()!! // This is DataUser
                Log.d("UserRepositoryImpl", "API profile update successful, updating cache and DB.")
                // Update local DB and Cache
                withContext(Dispatchers.IO) {
                    userDao.insertUser(updatedNetworkUser) // Update DB
                    // Update cache
                    val userType: Type = DataUser::class.java
                    cacheManager.cacheData(CURRENT_USER_CACHE_KEY, updatedNetworkUser, userType, USER_CACHE_EXPIRY_MS)
                }
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to update profile"))
        }
    }

    // Helper for updating privacy settings (invalidate cache on success?)
    private suspend fun updatePrivacySetting(updateDto: PrivacySettingsUpdateDto): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.updatePrivacySettings("Bearer $token", updateDto)
            }
            if (response.isSuccessful) {
                // TODO: Invalidate privacy settings cache if implemented
                Log.d("UserRepositoryImpl", "API privacy update successful.")
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to update privacy setting"))
        }
    }
    // Other privacy update methods call the helper...
    override suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(profileIsPublic = isPublic))
    override suspend fun updateListVisibility(arePublic: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(listsArePublic = arePublic))
    override suspend fun updateAnalytics(enabled: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(allowAnalytics = enabled))

    override suspend fun deleteAccount(): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.deleteAccount("Bearer $token")
            }
            if (response.isSuccessful || response.code() == 204) {
                Log.d("UserRepositoryImpl", "API account deletion successful. Clearing local data.")
                // Perform sign out which clears DB/Cache/Token
                signOut()
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to delete account"))
        }
    }

    // --- Error Mapping Helpers ---
    private fun mapApiError(code: Int, errorBody: String?): AppException { /* ... Keep implementation ... */ }
    private fun mapExceptionToAppException(e: Throwable, defaultMessage: String): AppException { /* ... Keep implementation ... */ }

}

// Mapper (Keep as is)
// fun DataUser.toDomain(): DomainUser { ... }