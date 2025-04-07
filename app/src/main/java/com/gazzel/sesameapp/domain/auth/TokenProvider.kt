// Create a new package: domain/auth
// Create file: domain/auth/TokenProvider.kt
package com.gazzel.sesameapp.domain.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // Make it a singleton managed by Hilt
class TokenProvider @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    // Simple in-memory cache (consider more robust caching if needed)
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0L // Unix timestamp in seconds

    // Threshold in seconds (e.g., 5 minutes before actual expiry)
    private val refreshThresholdSeconds: Long = TimeUnit.MINUTES.toSeconds(5)

    suspend fun getToken(): String? {
        val currentTimeSeconds = System.currentTimeMillis() / 1000
        // Check cache and expiry threshold
        if (cachedToken != null && currentTimeSeconds < (tokenExpiry - refreshThresholdSeconds)) {
            Log.d("TokenProvider", "Using cached token.")
            return cachedToken
        } else {
            Log.d("TokenProvider", "Refreshing token.")
            return refreshToken()
        }
    }

    private suspend fun refreshToken(): String? {
        return try {
            val user = firebaseAuth.currentUser
            if (user == null) {
                Log.e("TokenProvider", "No current user logged in.")
                cachedToken = null
                tokenExpiry = 0L
                return null
            }
            // Force refresh by passing true
            val result = user.getIdToken(true).await()
            cachedToken = result.token
            // Firebase expirationTimestamp is in seconds
            tokenExpiry = result.expirationTimestamp
            Log.d("TokenProvider", "Refreshed token expires at $tokenExpiry (Unix timestamp)")
            cachedToken
        } catch (e: Exception) {
            Log.e("TokenProvider", "Token refresh failed", e)
            cachedToken = null
            tokenExpiry = 0L
            null // Return null on failure
        }
    }

    fun clearToken() {
        cachedToken = null
        tokenExpiry = 0L
    }
}