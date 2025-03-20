package com.gazzel.sesameapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        // Redirect if already signed in
        if (auth.currentUser != null) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("476131428056-je1pi2q8narnv7scq5dhbch1rbbdqbn2.apps.googleusercontent.com") // Your Web Client ID
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            val scope = rememberCoroutineScope()
            var isLoading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }

            MaterialTheme {
                LoginScreen(
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onGoogleSignInClick = {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val signInIntent = googleSignInClient.signInIntent
                            startActivityForResult(signInIntent, RC_SIGN_IN)
                        }
                    }
                )
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                setContent {
                    MaterialTheme {
                        LoginScreen(
                            isLoading = false,
                            errorMessage = "Sign-in failed: ${e.statusCode}",
                            onGoogleSignInClick = { startActivityForResult(GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signInIntent, RC_SIGN_IN) }
                        )
                    }
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                } else {
                    setContent {
                        MaterialTheme {
                            LoginScreen(
                                isLoading = false,
                                errorMessage = "Auth failed: ${task.exception?.message}",
                                onGoogleSignInClick = { startActivityForResult(GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signInIntent, RC_SIGN_IN) }
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onGoogleSignInClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to SesameApp",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onGoogleSignInClick,
                    enabled = !isLoading
                ) {
                    Text("Sign in with Google")
                }
            }
            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}