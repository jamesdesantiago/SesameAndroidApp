package com.gazzel.sesameapp.data.repository

// Import FastAPI models used by UserApiService methods if needed, e.g.:
// import com.gazzel.sesameapp.data.remote.UsernameSet
// import com.gazzel.sesameapp.data.remote.CheckUsernameResponse
import android.util.Log
import com.gazzel.sesameapp.data.local.dao.UserDao
import com.gazzel.sesameapp.data.remote.UserApiService
import com.gazzel.sesameapp.domain.exception.AppException
import com.gazzel.sesameapp.domain.model.PrivacySettings
import com.gazzel.sesameapp.domain.repository.UserRepository
import com.gazzel.sesameapp.domain.util.Result
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import com.gazzel.sesameapp.data.model.User as DataUser
import com.gazzel.sesameapp.domain.model.User as DomainUser

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val userDao: UserDao,
    private val firebaseAuth: FirebaseAuth
) : UserRepository {

    // Helper function to get the current Firebase Auth Token
    private suspend fun getAuthToken(): String? {
        return try {
            firebaseAuth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Failed to get auth token", e)
            null
        }
    }

    override suspend fun getCurrentUser(): Flow<DomainUser> = flow {
        // Attempt to fetch from API first (assuming UserApiService has a method)
        // This requires UserApiService to have a relevant endpoint like /users/me
        val token = getAuthToken()
        if (token != null) {
            try {
                // You need a method like this in UserApiService, mapped to your FastAPI /users/me or similar
                val response = userApiService.getCurrentUserProfile("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val dataUser = response.body()!!
                    userDao.insertUser(dataUser) // Cache the fetched user
                    emit(dataUser.toDomain()) // Emit the domain user
                    return@flow // Stop after successful API fetch
                } else {
                    Log.w("UserRepositoryImpl", "Failed to fetch user from API: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("UserRepositoryImpl", "API error fetching user", e)
                // Fall through to local cache on API error
            }
        } else {
            Log.w("UserRepositoryImpl", "No auth token, cannot fetch user from API.")
            // Fall through to local cache if no token
        }


        // Fallback to local cache
        val localUser = userDao.getCurrentUser()
        if (localUser != null) {
            Log.d("UserRepositoryImpl", "Emitting user from local cache.")
            emit(localUser.toDomain())
        } else {
            // If neither API nor cache works, throw error
            throw AppException.AuthException("No user data available locally or from API.")
        }
    }


    override suspend fun updateUsername(username: String): Result<Unit> { // Returns YOUR Result<Unit>
        val token = getAuthToken()
        if (token == null) {
            // Return YOUR Result.error if no token
            return Result.error(AppException.AuthException("User not authenticated"))
        }

        return try { // Use try-catch for the API call
            val response = userApiService.setUsername( // Call the method defined in UserApiService
                authorization = "Bearer $token",
                request = com.gazzel.sesameapp.data.remote.UsernameSet(username = username)
            )

            if (response.isSuccessful) {
                // Update local cache
                try {
                    val localUser = userDao.getCurrentUser()
                    if (localUser != null) {
                        userDao.insertUser(localUser.copy(username = username))
                    }
                } catch (dbError: Exception) {
                    Log.e("UserRepositoryImpl", "Failed to update local user cache", dbError)
                    // Decide if DB error should cause overall failure
                }
                Result.success(Unit) // Return YOUR Result.success
            } else {
                // API call failed (e.g., 4xx, 5xx)
                val errorDetail = response.errorBody()?.string() ?: response.message()
                val apiException = retrofit2.HttpException(response) // Create HttpException
                // Map the specific API exception and return YOUR Result.error
                Result.error(mapExceptionToAppException(apiException, "Failed to update username"))
            }
        } catch (e: Exception) {
            // Catch network errors (IOException) or other exceptions during the call
            // Map the exception and return YOUR Result.error
            Result.error(mapExceptionToAppException(e, "Failed to update username"))
        }
    }


    override suspend fun checkUsername(): Flow<Boolean> = flow {
        val token = getAuthToken()
        if (token == null) {
            Log.w("UserRepositoryImpl", "No auth token for checkUsername")
            // Emit 'true' maybe? Or throw error? Depends on desired UX
            emit(true) // Assume needs username if not authenticated? Or throw:
            // throw AppException.AuthException("User not authenticated")
            return@flow
        }
        try {
            // Assuming UserApiService has checkUsername mapped to GET /users/check-username
            val response = userApiService.checkUsername(authorization = "Bearer $token") // Replace with actual method name
            if (response.isSuccessful && response.body() != null) {
                emit(response.body()!!.needsUsername)
            } else {
                Log.e("UserRepositoryImpl", "checkUsername API failed: ${response.code()}")
                throw IOException("API Error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("UserRepositoryImpl", "Error checking username", e)
            // Decide how to handle check failure - maybe assume username is needed?
            emit(true) // Or rethrow: throw mapExceptionToAppException(e, "Failed to check username")
        }
    }

    // --- STUB IMPLEMENTATIONS FOR PRIVACY METHODS ---
    // These will use the API Service once backend endpoints are ready

    override suspend fun getPrivacySettings(): PrivacySettings {
        Log.w("UserRepositoryImpl", "STUB: getPrivacySettings() called. Returning default.")
        return PrivacySettings(
            profileIsPublic = true,
            listsArePublic = true,
            allowAnalytics = true
        )
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) { // Ensure runs off main thread if userDao is blocking
            firebaseAuth.signOut() // Sign out from Firebase Auth
            userDao.deleteAllUsers() // Clear local user cache
        }
        Result.success(Unit) // Explicit success needed for fold
    }.fold(
        onSuccess = { it }, // Return Result.success(Unit)
        onFailure = {
            Log.e("UserRepositoryImpl", "Sign out failed", it)
            // Don't necessarily return error, sign out might partially succeed
            // Or return specific error: Result.error(mapExceptionToAppException(it, "Sign out failed"))
            Result.success(Unit) // Or maybe just succeed anyway? Depends on desired behavior.
        }
    )


    override suspend fun updateProfile(displayName: String?, profilePictureUrl: String?): Result<Unit> {
        Log.w("UserRepositoryImpl", "STUB: updateProfile called. Simulating success.")
        // TODO: Implement API call to update profile details (needs backend endpoint)
        // Example:
        // val token = getAuthToken() ?: return Result.error(...)
        // return runCatching {
        //     val response = userApiService.updateUserProfile(token, UpdateProfileDto(displayName, profilePictureUrl))
        //     if(!response.isSuccessful) throw IOException(...)
        //     // Optionally update local cache:
        //     val localUser = userDao.getCurrentUser()?.copy(displayName=displayName, profilePicture=profilePictureUrl)
        //     if (localUser != null) userDao.insertUser(localUser)
        //     Result.success(Unit)
        // }.fold(...)
        return Result.success(Unit) // Simulate success for now
    }

    override suspend fun updateProfileVisibility(isPublic: Boolean): Result<Unit> {
        Log.w("UserRepositoryImpl", "STUB: updateProfileVisibility($isPublic) called. Simulating success.")
        return Result.success(Unit)
    }

    override suspend fun updateListVisibility(arePublic: Boolean): Result<Unit> {
        Log.w("UserRepositoryImpl", "STUB: updateListVisibility($arePublic) called. Simulating success.")
        return Result.success(Unit)
    }

    override suspend fun updateAnalytics(enabled: Boolean): Result<Unit> {
        Log.w("UserRepositoryImpl", "STUB: updateAnalytics($enabled) called. Simulating success.")
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        Log.w("UserRepositoryImpl", "STUB: deleteAccount() called. Simulating success.")
        try {
            userDao.deleteAllUsers()
        } catch (e: Exception) {
            Log.w("UserRepositoryImpl", "Failed to clear local user DB during simulated account deletion", e)
        }
        return Result.success(Unit)
    }

    // --- Helper Functions for Error Mapping (Adapted for API errors) ---
    private fun mapExceptionToAppException(e: Throwable, defaultMessage: String): AppException {
        Log.e("UserRepositoryImpl", "$defaultMessage: ${e.message}", e)
        return when (e) {
            is retrofit2.HttpException -> {
                val code = e.code()
                val message = e.message()
                AppException.NetworkException("API Error: $code $message", code = code, cause = e)
            }
            is IOException -> {
                AppException.NetworkException(e.message ?: "Network error", cause = e)
            }
            is AppException -> e
            else -> AppException.UnknownException(e.message ?: defaultMessage, e)
        }
    }

    private fun mapExceptionToResultError(e: Throwable, defaultMessage: String): Result<Unit> {
        // The actual return value is still Result.error, which is compatible.
        return Result.error(mapExceptionToAppException(e, defaultMessage))
    }

}

// Mapper needs to handle potential Int ID from API/DB vs String ID in Domain
// Make sure DomainUser expects String ID
fun DataUser.toDomain(): DomainUser {
    return DomainUser(
        id = this.id.toString(),
        email = this.email,
        username = this.username,
        displayName = this.displayName,
        profilePicture = this.profilePicture
    )
}

// Optional: Reverse mapper if needed (Domain String ID to API/DB Int ID)
// fun DomainUser.toData(): DataUser {
//     return DataUser(
//         id = this.id.toIntOrNull() ?: 0, // Handle potential conversion error
//         email = this.email,
//         username = this.username,
//         displayName = this.displayName,
//         profilePicture = this.profilePicture
//     )
// }