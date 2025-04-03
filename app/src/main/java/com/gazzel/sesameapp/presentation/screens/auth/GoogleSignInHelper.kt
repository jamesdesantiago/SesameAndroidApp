package com.gazzel.sesameapp.presentation.screens.auth

// --- Android & System Imports ---
import android.content.Context
import android.content.Intent // <-- IMPORT Intent
import android.content.IntentSender // <-- IMPORT IntentSender
import android.util.Log

// --- Dependency Injection Imports ---
import javax.inject.Inject // <-- IMPORT Inject
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext

// --- Google Identity Imports ---
import com.google.android.gms.auth.api.identity.BeginSignInRequest // <-- IMPORT BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException // <-- IMPORT ApiException

// --- Coroutine Imports ---
import kotlinx.coroutines.suspendCancellableCoroutine // <-- IMPORT suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await // <-- IMPORT await for Task
import kotlin.coroutines.resume // <-- IMPORT resume
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


// --- AuthModule remains the same ---
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideSignInClient(@ApplicationContext context: Context): SignInClient {
        return Identity.getSignInClient(context)
    }
}