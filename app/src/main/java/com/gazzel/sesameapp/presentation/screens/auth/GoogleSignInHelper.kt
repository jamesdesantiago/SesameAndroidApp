package com.gazzel.sesameapp.presentation.screens.auth

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import javax.inject.Singleton

import kotlin.coroutines.resumeWithException

class GoogleSignInHelper @Inject constructor(
    private val oneTapClient: SignInClient
) {
    private val signInRequest = BeginSignInRequest.builder()
        .setGoogleIdTokenRequestOptions(
            BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                .setSupported(true)
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("476131428056-je1pi2q8narnv7scq5dhbch1rbbdqbn2.apps.googleusercontent.com") // Replace with your server client ID
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

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideSignInClient(@ApplicationContext context: Context): SignInClient {
        return Identity.getSignInClient(context)
    }
}