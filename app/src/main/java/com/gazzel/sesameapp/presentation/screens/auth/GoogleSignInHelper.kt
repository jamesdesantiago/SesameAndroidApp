package com.gazzel.sesameapp.presentation.screens.auth

import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// --- GoogleSignInHelper Class ---
class GoogleSignInHelper @Inject constructor( // Inject should resolve now
    private val oneTapClient: SignInClient
) {
    private val signInRequest = BeginSignInRequest.builder() // BeginSignInRequest should resolve
        .setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder() // Should resolve
                .setSupported(true)
                .setFilterByAuthorizedAccounts(false)
                // Ensure this is your correct Web Client ID from Google Cloud Console / Firebase
                .setServerClientId("476131428056-je1pi2q8narnv7scq5dhbch1rbbdqbn2.apps.googleusercontent.com")
                .build()
        )
        .setAutoSelectEnabled(true)
        .build()

    suspend fun signIn(): IntentSender { // IntentSender should resolve
        return try {
            // await() should resolve now
            oneTapClient.beginSignIn(signInRequest).await().pendingIntent.intentSender
        } catch (e: Exception) {
            // Consider logging the exception here
            Log.e("GoogleSignInHelper", "BeginSignIn failed", e)
            throw e // Re-throw after logging or handle differently
        }
    }

    suspend fun handleSignInResult(data: Intent?): String { // Intent should resolve
        // suspendCancellableCoroutine should resolve now
        return suspendCancellableCoroutine { continuation ->
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken // Extract token
                if (idToken != null) {
                    continuation.resume(idToken) // Resume coroutine with token
                } else {
                    Log.w("GoogleSignInHelper", "Google ID token was null.")
                    continuation.resumeWithException(Exception("No ID token received"))
                }
                // ApiException should resolve now
            } catch (e: ApiException) {
                Log.e("GoogleSignInHelper", "handleSignInResult failed with ApiException: ${e.statusCode}", e)
                continuation.resumeWithException(e) // Resume with exception
            } catch (e: Exception) { // Catch other potential exceptions
                Log.e("GoogleSignInHelper", "handleSignInResult failed with general Exception", e)
                continuation.resumeWithException(Exception("Failed to handle sign in result: ${e.localizedMessage}", e))
            }
        }
    }
}