// data/repository/UserRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.manager.ICacheManager // <<< Import ICacheManager
import com.gazzel.sesameapp.data.mapper.toDomain // Import mapper
import com.gazzel.sesameapp.data.model.User as DataUser
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.data.remote.dto.PrivacySettingsUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UserProfileUpdateDto
import com.gazzel.sesameapp.data.remote.dto.UsernameSetDto // Corrected DTO name
import com.gazzel.sesameapp.domain.auth.TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.model.User as DomainUser
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.Result
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
    private val cacheManager: ICacheManager // <<< Inject CacheManager
) : UserRepository {

    // Cache constants
    companion object {
        private val USER_CACHE_EXPIRY_MS = TimeUnit.HOURS.toMillis(1) // 1 hour
        private const val CURRENT_USER_CACHE_KEY = "current_user"
        private val PRIVACY_SETTINGS_TYPE: Type = PrivacySettings::class.java // Type for PrivacySettings
        private const val PRIVACY_SETTINGS_CACHE_KEY = "privacy_settings"
        private val PRIVACY_SETTINGS_CACHE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(30) // 30 mins
        private val USER_DATA_TYPE: Type = DataUser::class.java // Type for DataUser (local/network model)
    }

    // --- Implement getCurrentUser as suspend fun returning Result ---
    override suspend fun getCurrentUser(): Result<DomainUser> {
        Log.d("UserRepositoryImpl", "Attempting to fetch current user (suspend fun)...")

        // 1. Try Cache
        try {
            val cachedUser = cacheManager.getCachedData<DataUser>(CURRENT_USER_CACHE_KEY, USER_DATA_TYPE)
            if (cachedUser != null) {
                Log.d("UserRepositoryImpl", "Cache Hit: Returning cached current user")
                return Result.success(cachedUser.toDomain())
            }
            Log.d("UserRepositoryImpl", "Cache Miss: current user")
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Cache Get Error for current user", e)
            // Logged, proceed to DB
        }

        // 2. Try Local DB
        try {
            val localUser = withContext(Dispatchers.IO) { userDao.getCurrentUser() }
            if (localUser != null) {
                Log.d("UserRepositoryImpl", "DB Hit: Returning user from local DB.")
                // Update cache with DB data
                try { cacheManager.cacheData(CURRENT_USER_CACHE_KEY, localUser, USER_DATA_TYPE, USER_CACHE_EXPIRY_MS) }
                catch(cacheEx: Exception) { Log.e("UserRepositoryImpl", "Cache Put Error for DB user", cacheEx) }
                return Result.success(localUser.toDomain())
            }
            Log.d("UserRepositoryImpl", "DB Miss: current user")
        } catch(e: Exception) {
            Log.e("UserRepositoryImpl", "DB Get Error for current user", e)
            // Logged, proceed to Network
        }

        // 3. Try Network
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))

        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.getCurrentUserProfile("Bearer $token")
            }
            if (response.isSuccessful && response.body() != null) {
                val networkUser = response.body()!! // This is DataUser
                Log.d("UserRepositoryImpl", "Network fetch successful.")
                // Save to DB & Cache
                try {
                    withContext(Dispatchers.IO) { userDao.insertUser(networkUser) }
                    cacheManager.cacheData(CURRENT_USER_CACHE_KEY, networkUser, USER_DATA_TYPE, USER_CACHE_EXPIRY_MS)
                } catch(localSaveEx: Exception){
                    Log.e("UserRepositoryImpl", "Error saving network user locally", localSaveEx)
                    // Don't fail the overall success, just log the local save error
                }
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

    // --- updateUsername: Already returns Result<Unit>, ensure cache update ---
    override suspend fun updateUsername(username: String): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.setUsername(
                    authorization = "Bearer $token",
                    request = UsernameSetDto(username = username)
                )
            }
            if (response.isSuccessful) {
                // Update local DB and Cache on success
                try {
                    withContext(Dispatchers.IO) {
                        val localUser = userDao.getCurrentUser()
                        if (localUser != null) {
                            val updatedUser = localUser.copy(username = username)
                            userDao.insertUser(updatedUser)
                            cacheManager.cacheData(CURRENT_USER_CACHE_KEY, updatedUser, USER_DATA_TYPE, USER_CACHE_EXPIRY_MS)
                            Log.d("UserRepositoryImpl", "Local DB and Cache updated with new username.")
                        } else {
                            cacheManager.cacheData<DataUser?>(CURRENT_USER_CACHE_KEY, null, USER_DATA_TYPE, -1L) // Invalidate
                            Log.w("UserRepositoryImpl", "Local user not found during username update cache.")
                        }
                    }
                } catch (localSaveEx: Exception) {
                    Log.e("UserRepositoryImpl", "Error saving updated username locally", localSaveEx)
                }
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to update username"))
        }
    }

    // --- Implement checkUsername as suspend fun returning Result ---
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

    // --- Implement getPrivacySettings returning Result ---
    override suspend fun getPrivacySettings(): Result<PrivacySettings> {
        val cacheKey = PRIVACY_SETTINGS_CACHE_KEY
        // 1. Try Cache
        try {
            val cachedSettings = cacheManager.getCachedData<PrivacySettings>(cacheKey, PRIVACY_SETTINGS_TYPE)
            if (cachedSettings != null) {
                Log.d("UserRepositoryImpl", "Cache Hit: Returning cached privacy settings")
                return Result.success(cachedSettings)
            }
            Log.d("UserRepositoryImpl", "Cache Miss: privacy settings")
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Cache Get Error for privacy settings", e)
        }
        // 2. Try Network
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.getPrivacySettings("Bearer $token")
            }
            if (response.isSuccessful && response.body() != null) {
                val settings = response.body()!!
                try { cacheManager.cacheData(cacheKey, settings, PRIVACY_SETTINGS_TYPE, PRIVACY_SETTINGS_CACHE_EXPIRY_MS) }
                catch (cacheEx: Exception) { Log.e("UserRepositoryImpl", "Cache Put Error for privacy settings", cacheEx)}
                Log.d("UserRepositoryImpl", "Network fetch for privacy settings successful.")
                Result.success(settings)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to get privacy settings"))
        }
    }

    // --- signOut: Clear cache ---
    override suspend fun signOut(): Result<Unit> {
        return try {
            Log.d("UserRepositoryImpl", "Signing out user...")
            try {
                cacheManager.clearAllCache() // Clear all cache on sign out
                Log.d("UserRepositoryImpl", "Cache cleared.")
            } catch (cacheEx: Exception) {
                Log.e("UserRepositoryImpl", "Failed to clear cache during sign out", cacheEx)
            }
            withContext(Dispatchers.Default) { firebaseAuth.signOut() }
            withContext(Dispatchers.IO) { userDao.deleteAllUsers() }
            tokenProvider.clearToken()
            Log.d("UserRepositoryImpl", "Sign out successful.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Sign out failed", e)
            Result.error(mapExceptionToAppException(e, "Sign out failed"))
        }
    }

    // --- updateProfile: Update cache ---
    override suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        if (displayName == null && profilePictureUrl == null) return Result.success(Unit)

        return try {
            val updateDto = UserProfileUpdateDto(displayName = displayName, profilePicture = profilePictureUrl)
            val response = withContext(Dispatchers.IO) {
                userApiService.updateUserProfile("Bearer $token", updateDto)
            }
            if (response.isSuccessful && response.body() != null) {
                val updatedNetworkUser = response.body()!!
                Log.d("UserRepositoryImpl", "API profile update successful, updating cache and DB.")
                try {
                    withContext(Dispatchers.IO) { userDao.insertUser(updatedNetworkUser) }
                    cacheManager.cacheData(CURRENT_USER_CACHE_KEY, updatedNetworkUser, USER_DATA_TYPE, USER_CACHE_EXPIRY_MS)
                } catch (localSaveEx: Exception) {
                    Log.e("UserRepositoryImpl", "Error saving updated profile locally", localSaveEx)
                }
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to update profile"))
        }
    }

    // --- updatePrivacySetting helper: Invalidate cache ---
    private suspend fun updatePrivacySetting(updateDto: PrivacySettingsUpdateDto): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.updatePrivacySettings("Bearer $token", updateDto)
            }
            if (response.isSuccessful) {
                try { cacheManager.cacheData<PrivacySettings?>(PRIVACY_SETTINGS_CACHE_KEY, null, PRIVACY_SETTINGS_TYPE, -1L) } // Invalidate
                catch (cacheEx: Exception) { Log.e("UserRepositoryImpl", "Cache Invalidation Error for privacy settings", cacheEx) }
                Log.d("UserRepositoryImpl", "API privacy update successful & cache invalidated.")
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to update privacy setting"))
        }
    }
    // --- Other privacy methods call the helper ---
    override suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(profileIsPublic = isPublic))
    override suspend fun updateListVisibility(arePublic: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(listsArePublic = arePublic))
    override suspend fun updateAnalytics(enabled: Boolean): Result<Unit> = updatePrivacySetting(PrivacySettingsUpdateDto(allowAnalytics = enabled))


    // --- deleteAccount: ensure signOut (which clears cache) is called ---
    override suspend fun deleteAccount(): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            val response = withContext(Dispatchers.IO) {
                userApiService.deleteAccount("Bearer $token")
            }
            if (response.isSuccessful || response.code() == 204) {
                Log.d("UserRepositoryImpl", "API account deletion successful. Clearing local data via signOut.")
                signOut() // Sign out handles clearing cache/db/token
                Result.success(Unit)
            } else {
                Result.error(mapApiError(response.code(), response.errorBody()?.string()))
            }
        } catch (e: Exception) {
            Result.error(mapExceptionToAppException(e, "Failed to delete account"))
        }
    }

    // --- Error Mapping Helpers (Keep as they are) ---
    private fun mapApiError(code: Int, errorBody: String?): AppException { /* ... */ }
    private fun mapExceptionToAppException(e: Throwable, defaultMessage: String): AppException { /* ... */ }

} // End class