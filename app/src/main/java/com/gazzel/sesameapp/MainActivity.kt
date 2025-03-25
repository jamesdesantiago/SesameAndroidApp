package com.gazzel.sesameapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gazzel.sesameapp.ui.theme.SesameAppTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.GetTokenResult
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            val scope = rememberCoroutineScope()
            var isLoading by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var needsUsername by remember { mutableStateOf(false) }
            var isCheckingUsername by remember { mutableStateOf(false) }

            SesameAppTheme {
                if (auth.currentUser != null) {
                    // Check if the user needs to set a username
                    LaunchedEffect(isCheckingUsername) {
                        if (!isCheckingUsername) {
                            isCheckingUsername = true
                            scope.launch {
                                try {
                                    if (auth.currentUser == null) {
                                        throw Exception("No authenticated user found")
                                    }
                                    val getTokenResult: GetTokenResult? = auth.currentUser?.getIdToken(true)?.await()
                                    if (getTokenResult == null) {
                                        throw Exception("Failed to fetch token")
                                    }
                                    val token: String? = getTokenResult.getToken()
                                    if (token == null || token.isEmpty()) {
                                        throw Exception("Fetched token is empty or null")
                                    }
                                    val authorizationHeader = "Bearer $token"
                                    // Use the token in your API call
                                    val response = ApiClient.usernameService.checkUsername(
                                        authorization = authorizationHeader
                                    )
                                    if (response.isSuccessful) {
                                        // Handle successful response
                                    } else {
                                        throw Exception("API call failed: ${response.message()}")
                                    }
                                } catch (e: Exception) {
                                    // Handle errors (e.g., show an error message)
                                    auth.signOut() // Optional: sign out on error
                                }
                            }
                        }
                    }

                    if (errorMessage != null) {
                        // Show error if username check fails
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
                                    text = errorMessage ?: "Unknown error",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        auth.signOut()
                                        // Reset state to show LoginScreen
                                        isCheckingUsername = false
                                        needsUsername = false
                                        errorMessage = null
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        text = "Sign Out and Try Again",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    } else if (needsUsername) {
                        UsernameScreen(
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onSetUsername = { username ->
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val token = auth.currentUser?.getIdToken(true)?.await()
                                            ?: throw Exception("No token available")
                                        val response = ApiClient.usernameService.setUsername(
                                            authorization = "Bearer $token",
                                            request = UsernameRequest(username)
                                        )
                                        if (response.isSuccessful) {
                                            needsUsername = false // Proceed to NewHomeScreen
                                        } else {
                                            // Parse the error body to get the detailed message
                                            val errorBody = response.errorBody()?.string()
                                            val errorMessageJson = errorBody?.let {
                                                Gson().fromJson(it, JsonObject::class.java)
                                            }
                                            errorMessage = errorMessageJson?.get("detail")?.asString
                                                ?: "Failed to set username: ${response.message()}"
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Error: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        )
                    } else {
                        NewHomeScreen()
                    }
                } else {
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build()
                    val googleSignInClient = GoogleSignIn.getClient(this, gso)

                    LoginScreen(
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onGoogleSignInClick = {
                            isLoading = true
                            errorMessage = null
                            val signInIntent = googleSignInClient.signInIntent
                            startActivityForResult(signInIntent, RC_SIGN_IN)
                        },
                        onAppleSignInClick = {
                            isLoading = true
                            errorMessage = null
                            // Placeholder for Apple Sign-In implementation
                            // TODO: Implement Apple Sign-In
                            isLoading = false
                            errorMessage = "Apple Sign-In not implemented yet"
                        }
                    )
                }
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
                    SesameAppTheme {
                        LoginScreen(
                            isLoading = false,
                            errorMessage = "Sign-in failed: ${e.statusCode}",
                            onGoogleSignInClick = {
                                startActivityForResult(
                                    GoogleSignIn.getClient(
                                        this,
                                        GoogleSignInOptions.DEFAULT_SIGN_IN
                                    ).signInIntent, RC_SIGN_IN
                                )
                            },
                            onAppleSignInClick = {
                                // Placeholder for Apple Sign-In
                            }
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
                    println("Firebase Auth Success: User = ${auth.currentUser?.email}")
                    // The LaunchedEffect in setContent will handle checking if a username is needed
                } else {
                    println("Firebase Auth Failed: ${task.exception?.message}")
                    setContent {
                        SesameAppTheme {
                            LoginScreen(
                                isLoading = false,
                                errorMessage = "Auth failed: ${task.exception?.message}",
                                onGoogleSignInClick = {
                                    startActivityForResult(
                                        GoogleSignIn.getClient(
                                            this,
                                            GoogleSignInOptions.DEFAULT_SIGN_IN
                                        ).signInIntent, RC_SIGN_IN
                                    )
                                },
                                onAppleSignInClick = {
                                    // Placeholder for Apple Sign-In
                                }
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
    onGoogleSignInClick: () -> Unit,
    onAppleSignInClick: () -> Unit
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
            // App Title
            Text(
                text = "Sesame",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                // Continue with Google Button
                Button(
                    onClick = onGoogleSignInClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified // Preserve original colors of the logo
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue with Google",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Continue with Apple Button
                Button(
                    onClick = onAppleSignInClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_apple),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified // Preserve original colors of the logo
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Continue with Apple",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Terms and Privacy Policy Text
                val annotatedString = buildAnnotatedString {
                    append("By clicking continue, you agree to our ")

                    // Terms of Service link
                    withStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Terms of Service")
                    }

                    append(" and ")

                    // Privacy Policy link
                    withStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append("Privacy Policy")
                    }
                    append(".")
                }

                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = true,
                            onClick = {
                                // Placeholder for handling clicks on Terms of Service and Privacy Policy
                                // You can add logic here to detect which part was clicked
                            }
                        )
                )
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun UsernameScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSetUsername: (String) -> Unit
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
            // App Title
            Text(
                text = "Sesame",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(48.dp))

            var username by remember { mutableStateOf("") }
            var clientError by remember { mutableStateOf<String?>(null) }

            // Username Input Field
            OutlinedTextField(
                value = username,
                onValueChange = { newUsername ->
                    username = newUsername
                    // Client-side validation
                    clientError = validateUsername(newUsername)
                },
                label = { Text("choose a username...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = clientError != null || errorMessage != null
            )

            // Show client-side validation error
            clientError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            // Show server-side error (e.g., username taken)
            errorMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Done Button
            Button(
                onClick = {
                    if (clientError == null) {
                        onSetUsername(username)
                    }
                },
                enabled = !isLoading && username.isNotEmpty() && clientError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "done!",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

fun validateUsername(username: String): String? {
    // Instagram username rules
    if (username.isEmpty()) {
        return "Username cannot be empty"
    }
    if (username.length > 30) {
        return "Username must be 30 characters or less"
    }
    if (!Pattern.matches("^[a-zA-Z0-9._]+$", username)) {
        return "Username can only contain letters, numbers, periods, and underscores"
    }
    if (username.contains(" ")) {
        return "Username cannot contain spaces"
    }
    return null
}