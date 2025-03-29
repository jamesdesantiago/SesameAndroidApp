package com.gazzel.sesameapp.presentation.screens.auth

import android.content.Intent
import android.content.IntentSender
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

import kotlin.coroutines.resumeWithException

class GoogleSignInHelper @Inject constructor(
    private val oneTapClient: SignInClient
) {
    private val signInRequest = BeginSignInRequest.builder()
        .setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("YOUR_SERVER_CLIENT_ID") // Replace with your server client ID
                .build()
        )
        .setAutoSelectEnabled(true)
        .build()

    suspend fun signIn(): IntentSender {
        return try {
            oneTapClient.beginSignIn(signInRequest).await().pendingIntent.intentSender
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun handleSignInResult(data: Intent?): String {
        return suspendCancellableCoroutine { continuation ->
            try {
                val credential = oneTapClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken
                if (idToken != null) {
                    continuation.resume(idToken)
                } else {
                    continuation.resumeWithException(Exception("No ID token received"))
                }
            } catch (e: ApiException) {
                continuation.resumeWithException(e)
            }
        }
    }
} 