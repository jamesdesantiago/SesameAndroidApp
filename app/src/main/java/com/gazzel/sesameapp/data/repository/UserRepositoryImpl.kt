// data/repository/UserRepositoryImpl.kt
package com.gazzel.sesameapp.data.repository

import android.util.Log
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.remote.UserApiService // Assuming this service remains for now
// Import DTOs used by UserApiService (Ensure these match the actual service methods)
import com.gazzel.sesameapp.data.remote.UsernameSet
import com.gazzel.sesameapp.data.remote.dto.UserProfileUpdateDto
import com.gazzel.sesameapp.data.remote.dto.PrivacySettingsUpdateDto
// Import Domain models and Repository interface
import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.model.User as DomainUser
import com.gazzel.sesameapp.domain.repository.UserRepository
// Import Result, Exceptions, TokenProvider
import com.gazzel.sesameapp.domain.auth.TokenProvider // <<< CHANGE: Inject TokenProvider
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.util.Result
import com.gazzel.sesameapp.domain.util.map // For mapping Result
// Import Data layer User model (Entity)
import com.gazzel.sesameapp.data.model.User as DataUser
// Coroutine stuff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
// Need Firebase Auth ONLY for direct sign-out, keep it for now just for that method.
import com.google.firebase.auth.FirebaseAuth

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService, // <<< CHANGE to use consolidated UserApiService
    private val userDao: UserDao,
    private val tokenProvider: TokenProvider,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    // --- Flow for Observing Current User (API-first, Cache-fallback) ---
    override fun getCurrentUser(): Flow<DomainUser> = flow {
        Log.d("UserRepositoryImpl", "Attempting to fetch current user...")
        val token = tokenProvider.getToken() // Get token once

        if (token != null) {
            try {
                val response = withContext(Dispatchers.IO) { // <<< IO Dispatcher for API call
                    Log.d("UserRepositoryImpl", "Fetching user from API...")
                    userApiService.getCurrentUserProfile("Bearer $token")
                }
                if (response.isSuccessful && response.body() != null) {
                    val dataUser = response.body()!!
                    Log.d("UserRepositoryImpl", "API fetch successful, saving to cache.")
                    withContext(Dispatchers.IO) { // <<< IO Dispatcher for DB write
                        userDao.insertUser(dataUser)
                    }
                    emit(dataUser.toDomain()) // Emit the domain user from API result
                    return@flow // Stop after successful API fetch
                } else {
                    Log.w("UserRepositoryImpl", "API fetch failed: ${response.code()}. Falling back to cache.")
                }
            } catch (e: Exception) {
                Log.e("UserRepositoryImpl", "API error fetching user, falling back to cache.", e)
                // Fall through to local cache on API error
            }
        } else {
            Log.w("UserRepositoryImpl", "No auth token, trying local cache.")
            // Fall through to local cache if no token
        }

        // Fallback to local cache
        val localUser = withContext(Dispatchers.IO) { // <<< IO Dispatcher for DB read
            Log.d("UserRepositoryImpl", "Fetching user from local cache.")
            userDao.getCurrentUser()
        }
        if (localUser != null) {
            Log.d("UserRepositoryImpl", "Emitting user from local cache.")
            emit(localUser.toDomain())
        } else {
            // If neither API nor cache works, emit error in the flow
            Log.e("UserRepositoryImpl", "No user data available locally or from API.")
            throw AppException.AuthException("User data not available") // Throw exception within flow
        }
    }.catch { e -> // Catch exceptions from the flow builder itself
        Log.e("UserRepositoryImpl", "Error in getCurrentUser flow", e)
        // You might want to re-throw or handle differently depending on how the UI consumes this flow's errors
        throw AppException.UnknownException("Failed to get user data", e)
    }.flowOn(Dispatchers.Default) // Use Default dispatcher for flow logic, IO is used inside for specific calls


    // --- Suspend function for updating username ---
    override suspend fun updateUsername(username: String): Result<Unit> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("UserRepositoryImpl", "UpdateUsername failed: No token.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }

        return try {
            withContext(Dispatchers.IO) { // <<< IO Dispatcher for API and DB
                // API Call
                Log.d("UserRepositoryImpl", "Calling API to set username: $username")
                val response = userApiService.setUsername(
                    authorization = "Bearer $token",
                    request = UsernameSet(username = username) // Ensure UsernameSet DTO exists/is correct
                )

                if (response.isSuccessful) {
                    // Update local cache on success
                    Log.d("UserRepositoryImpl", "API username update successful, updating cache.")
                    val localUser = userDao.getCurrentUser() // Read happens on IO
                    if (localUser != null) {
                        userDao.insertUser(localUser.copy(username = username)) // Write happens on IO
                        Log.d("UserRepositoryImpl", "Local cache updated with new username.")
                    } else {
                        Log.w("UserRepositoryImpl", "Local user not found in cache during username update.")
                    }
                    Result.success(Unit) // Return Success
                } else {
                    // API call failed
                    val errorMsg = "API Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    Log.e("UserRepositoryImpl", "UpdateUsername failed: $errorMsg")
                    Result.error(mapApiError(response.code(), response.errorBody()?.string())) // Map specific error
                }
            }
        } catch (e: Exception) {
            // Catch network or other exceptions
            Log.e("UserRepositoryImpl", "Exception during updateUsername", e)
            Result.error(mapExceptionToAppException(e, "Failed to update username"))
        }
    }

    // --- Flow for checking if username needs setup ---
    override fun checkUsername(): Flow<Boolean> = flow {
        Log.d("UserRepositoryImpl", "Checking username status...")
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("UserRepositoryImpl", "Cannot check username: No auth token.")
            // Decide behavior without token: assume needed or error? Assuming needed.
            emit(true)
            return@flow
        }

        try {
            val response = withContext(Dispatchers.IO) { // <<< IO Dispatcher for API call
                Log.d("UserRepositoryImpl", "Calling API to check username...")
                userApiService.checkUsername(authorization = "Bearer $token")
            }

            if (response.isSuccessful && response.body() != null) {
                Log.d("UserRepositoryImpl", "API check username successful: NeedsUsername=${response.body()!!.needsUsername}")
                emit(response.body()!!.needsUsername)
            } else {
                Log.e("UserRepositoryImpl", "API check username failed: ${response.code()}. Assuming username is needed.")
                // Assume needed on API error? Or throw?
                emit(true)
                // Alternative: throw mapApiError(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception checking username. Assuming username is needed.", e)
            // Assume needed on general exception?
            emit(true)
            // Alternative: throw mapExceptionToAppException(e, "Failed to check username status")
        }
    }.catch { e -> // Catch errors thrown within the flow
        Log.e("UserRepositoryImpl", "Error in checkUsername flow. Assuming username needed.", e)
        emit(true) // Emit default value on error
    }.flowOn(Dispatchers.Default) // Use Default dispatcher for flow logic


    // --- Suspend function for getting privacy settings ---
    override suspend fun getPrivacySettings(): Result<PrivacySettings> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("UserRepositoryImpl", "GetPrivacySettings failed: No token.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }

        return try {
            withContext(Dispatchers.IO) { // <<< IO Dispatcher for API call
                Log.d("UserRepositoryImpl", "Calling API to get privacy settings...")
                val response = userApiService.getPrivacySettings("Bearer $token")

                if (response.isSuccessful && response.body() != null) {
                    Log.d("UserRepositoryImpl", "API get privacy settings successful.")
                    // Assuming response.body() directly matches domain PrivacySettings
                    // If not, add a mapper here.
                    Result.success(response.body()!!)
                } else {
                    val errorMsg = "API Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    Log.e("UserRepositoryImpl", "GetPrivacySettings failed: $errorMsg")
                    Result.error(mapApiError(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception during getPrivacySettings", e)
            Result.error(mapExceptionToAppException(e, "Failed to get privacy settings"))
        }
    }

    // --- Suspend function for signing out ---
    override suspend fun signOut(): Result<Unit> {
        return try {
            Log.d("UserRepositoryImpl", "Signing out user...")
            // Firebase sign out can likely run on the calling dispatcher or Default
            withContext(Dispatchers.Default) {
                firebaseAuth.signOut() // Sign out from Firebase Auth
            }
            withContext(Dispatchers.IO) { // <<< IO Dispatcher for DB write
                Log.d("UserRepositoryImpl", "Clearing local user cache.")
                userDao.deleteAllUsers() // Clear local user cache
            }
            Log.d("UserRepositoryImpl", "Sign out successful.")
            Result.success(Unit)
        } catch(e: Exception) {
            Log.e("UserRepositoryImpl", "Sign out failed", e)
            Result.error(mapExceptionToAppException(e, "Sign out failed"))
        }
    }

    // --- Suspend function for updating profile ---
    override suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit> {
        val token = tokenProvider.getToken()
        if (token == null) {
            Log.w("UserRepositoryImpl", "UpdateProfile failed: No token.")
            return Result.error(AppException.AuthException("User not authenticated"))
        }

        // Ensure at least one field is being updated
        if (displayName == null && profilePictureUrl == null) {
            Log.w("UserRepositoryImpl", "UpdateProfile called with no changes.")
            return Result.success(Unit) // Nothing to update
        }

        return try {
            withContext(Dispatchers.IO) { // <<< IO Dispatcher for API and DB
                // Prepare DTO (assuming UserProfileUpdateDto exists in data/remote/dto)
                val updateDto = UserProfileUpdateDto(
                    displayName = displayName,
                    profilePicture = profilePictureUrl // API might expect different field name
                )

                // Call API (assuming endpoint exists in UserApiService)
                Log.d("UserRepositoryImpl", "Calling API to update profile...")
                // --- Placeholder for API Call ---
                // val response = userApiService.updateUserProfile("Bearer $token", updateDto) // Replace with actual call
                // --- Simulate success for now ---
                val responseCode = 200 // Simulate success
                val successful = responseCode in 200..299
                // --- End Placeholder ---


                if (successful) { // Replace with response.isSuccessful
                    Log.d("UserRepositoryImpl", "API profile update successful, updating cache.")
                    // Update local cache if needed
                    val localUser = userDao.getCurrentUser()
                    if (localUser != null) {
                        val updatedUser = localUser.copy(
                            displayName = displayName ?: localUser.displayName,
                            profilePicture = profilePictureUrl ?: localUser.profilePicture
                        )
                        userDao.insertUser(updatedUser)
                        Log.d("UserRepositoryImpl", "Local cache updated after profile update.")
                    } else {
                        Log.w("UserRepositoryImpl", "Local user not found in cache during profile update.")
                    }
                    Result.success(Unit)
                } else {
                    // Handle API error
                    val errorMsg = "API Profile Update Error $responseCode" // Replace with actual error handling
                    Log.e("UserRepositoryImpl", "UpdateProfile failed: $errorMsg")
                    Result.error(mapApiError(responseCode, "Simulated error")) // Replace with actual mapping
                }
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception during updateProfile", e)
            Result.error(mapExceptionToAppException(e, "Failed to update profile"))
        }
    }


    // --- Methods for updating individual privacy settings ---
    // These follow a similar pattern: get token, call API in IO context, handle result

    override suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit> {
        return updatePrivacySetting(PrivacySettingsUpdateDto(profileIsPublic = isPublic))
    }

    override suspend fun updateListVisibility(arePublic: Boolean): Result<Unit> {
        return updatePrivacySetting(PrivacySettingsUpdateDto(listsArePublic = arePublic))
    }

    override suspend fun updateAnalytics(enabled: Boolean): Result<Unit> {
        return updatePrivacySetting(PrivacySettingsUpdateDto(allowAnalytics = enabled))
    }

    // Helper for updating privacy settings
    private suspend fun updatePrivacySetting(updateDto: PrivacySettingsUpdateDto) : Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            withContext(Dispatchers.IO) {
                Log.d("UserRepositoryImpl", "Calling API to update privacy settings: $updateDto")
                // Assuming endpoint exists in UserApiService
                val response = userApiService.updatePrivacySettings("Bearer $token", updateDto) // Use actual method

                if (response.isSuccessful) {
                    Log.d("UserRepositoryImpl", "API privacy update successful.")
                    // Optionally update a local cache of settings if you have one
                    Result.success(Unit)
                } else {
                    val errorMsg = "API Privacy Update Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    Log.e("UserRepositoryImpl", errorMsg)
                    Result.error(mapApiError(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception updating privacy setting", e)
            Result.error(mapExceptionToAppException(e, "Failed to update privacy setting"))
        }
    }


    // --- Delete Account ---
    override suspend fun deleteAccount(): Result<Unit> {
        val token = tokenProvider.getToken() ?: return Result.error(AppException.AuthException("User not authenticated"))
        return try {
            withContext(Dispatchers.IO) { // IO for API call and potentially DB clear
                Log.d("UserRepositoryImpl", "Calling API to delete account...")
                val response = userApiService.deleteAccount("Bearer $token") // Assuming endpoint exists

                if (response.isSuccessful || response.code() == 204) {
                    Log.d("UserRepositoryImpl", "API account deletion successful. Clearing local data.")
                    // Clear local data associated with the user
                    userDao.deleteAllUsers()
                    // TODO: Clear other related user data (prefs, cache keys, etc.)
                    // Consider calling signOut() logic as well
                    withContext(Dispatchers.Default) { firebaseAuth.signOut() }
                    Result.success(Unit)
                } else {
                    val errorMsg = "API Delete Account Error ${response.code()}: ${response.errorBody()?.string() ?: response.message()}"
                    Log.e("UserRepositoryImpl", errorMsg)
                    Result.error(mapApiError(response.code(), response.errorBody()?.string()))
                }
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Exception deleting account", e)
            Result.error(mapExceptionToAppException(e, "Failed to delete account"))
        }
    }

    // --- Helper Functions for Error Mapping (Adapted for API errors) ---
    // Can be shared or duplicated from ListRepositoryImpl
    private fun mapApiError(code: Int, errorBody: String?): AppException {
        Log.e("UserRepositoryImpl", "API Error $code: ${errorBody ?: "Unknown error"}")
        return when (code) {
            400 -> AppException.ValidationException(errorBody ?: "Bad Request")
            401 -> AppException.AuthException(errorBody ?: "Unauthorized")
            403 -> AppException.AuthException(errorBody ?: "Forbidden")
            404 -> AppException.ResourceNotFoundException(errorBody ?: "Not Found")
            409 -> AppException.ValidationException(errorBody ?: "Conflict (e.g., username exists)") // Example for 409
            in 500..599 -> AppException.NetworkException("Server Error ($code): ${errorBody ?: ""}", code)
            else -> AppException.NetworkException("Network Error ($code): ${errorBody ?: ""}", code)
        }
    }

    private fun mapExceptionToAppException(e: Throwable, defaultMessage: String): AppException {
        Log.e("UserRepositoryImpl", "$defaultMessage: ${e.message}", e)
        return when (e) {
            is retrofit2.HttpException -> { // Catch Retrofit-specific HTTP exception
                val code = e.code()
                val message = e.response()?.errorBody()?.string() ?: e.message()
                mapApiError(code, message) // Reuse API error mapping
            }
            is IOException -> { // Catch general IO exceptions (network issues)
                AppException.NetworkException(e.message ?: "Network error", cause = e)
            }
            is AppException -> e // Don't re-wrap existing AppExceptions
            else -> AppException.UnknownException(e.message ?: defaultMessage, e)
        }
    }

}

// Mapper (Keep as is, ensure ID logic is correct if API/DB differ)
fun DataUser.toDomain(): DomainUser {
    return DomainUser(
        id = this.id.toString(), // Assuming domain uses String ID
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}