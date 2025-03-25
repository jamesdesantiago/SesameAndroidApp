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
            var usernameCheckComplete by remember { mutableStateOf(false) }

            SesameAppTheme {
                if (auth.currentUser != null) {
                    // Check if the user needs to set a username
                    LaunchedEffect(auth.currentUser) {
                        if (!usernameCheckComplete) {
                            isLoading = true
                            scope.launch {
                                try {
                                    if (auth.currentUser == null) {
                                        throw Exception("No authenticated user found")
                                    }

                                    val token = auth.currentUser?.getIdToken(true)?.await()?.token
                                        ?: throw Exception("Failed to fetch token")

                                    val authorizationHeader = "Bearer $token"

                                    // Use the token to check if username is needed
                                    val response = ApiClient.usernameService.checkUsername(
                                        authorization = authorizationHeader
                                    )

                                    if (response.isSuccessful) {
                                        val checkUsernameResponse = response.body()
                                        if (checkUsernameResponse != null) {
                                            needsUsername = checkUsernameResponse.needsUsername
                                            usernameCheckComplete = true
                                        } else {
                                            throw Exception("Empty response body")
                                        }
                                    } else {
                                        // Parse the error message if possible
                                        val errorBody = response.errorBody()?.string()
                                        val errorMessageJson = errorBody?.let {
                                            Gson().fromJson(it, JsonObject::class.java)
                                        }
                                        val errorMsg = errorMessageJson?.get("detail")?.asString
                                            ?: "Failed to check username status: ${response.message()}"
                                        throw Exception(errorMsg)
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error checking username: ${e.message}"
                                    // On error, assume the user needs a username to be safe
                                    needsUsername = true
                                    usernameCheckComplete = true
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }

                    // Show loading screen while checking username status
                    if (!usernameCheckComplete || isLoading) {
                        LoadingScreen()
                    }
                    // Show error screen if there's an error
                    else if (errorMessage != null) {
                        ErrorScreen(
                            errorMessage = errorMessage ?: "Unknown error",
                            onRetry = {
                                // Reset states and try again
                                errorMessage = null
                                usernameCheckComplete = false
                            },
                            onSignOut = {
                                auth.signOut()
                                errorMessage = null
                                usernameCheckComplete = false
                                needsUsername = false
                            }
                        )
                    }
                    // Show username creation screen if needed
                    else if (needsUsername) {
                        UsernameScreen(
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onSetUsername = { username ->
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    try {
                                        val token = auth.currentUser?.getIdToken(true)?.await()?.token
                                            ?: throw Exception("No token available")

                                        val response = ApiClient.usernameService.setUsername(
                                            authorization = "Bearer $token",
                                            request = UsernameRequest(username)
                                        )

                                        if (response.isSuccessful) {
                                            // Username successfully set
                                            needsUsername = false
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
                    }
                    // Show main app screen if all checks pass
                    else {
                        NewHomeScreen()
                    }
                } else {
                    // User is not logged in, show login screen
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
                            // TODO: Implement Apple Sign-In
                            // For now, display a message that it's coming soon
                            isLoading = false
                            errorMessage = "Apple Sign-In coming soon"
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
                    // Username check will happen in LaunchedEffect in setContent
                    setContent {
                        SesameAppTheme {
                            val scope = rememberCoroutineScope()
                            var isLoading by remember { mutableStateOf(true) }
                            var errorMessage by remember { mutableStateOf<String?>(null) }
                            var needsUsername by remember { mutableStateOf(false) }
                            var usernameCheckComplete by remember { mutableStateOf(false) }

                            if (auth.currentUser != null) {
                                // Check if the user needs to set a username
                                LaunchedEffect(auth.currentUser) {
                                    if (!usernameCheckComplete) {
                                        scope.launch {
                                            try {
                                                val token = auth.currentUser?.getIdToken(true)?.await()?.token
                                                    ?: throw Exception("Failed to fetch token")

                                                val authorizationHeader = "Bearer $token"

                                                val response = ApiClient.usernameService.checkUsername(
                                                    authorization = authorizationHeader
                                                )

                                                if (response.isSuccessful) {
                                                    val checkUsernameResponse = response.body()
                                                    if (checkUsernameResponse != null) {
                                                        needsUsername = checkUsernameResponse.needsUsername
                                                        usernameCheckComplete = true
                                                    } else {
                                                        throw Exception("Empty response body")
                                                    }
                                                } else {
                                                    val errorBody = response.errorBody()?.string()
                                                    val errorMessageJson = errorBody?.let {
                                                        Gson().fromJson(it, JsonObject::class.java)
                                                    }
                                                    val errorMsg = errorMessageJson?.get("detail")?.asString
                                                        ?: "Failed to check username status: ${response.message()}"
                                                    throw Exception(errorMsg)
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Error checking username: ${e.message}"
                                                // Assume username is needed on error
                                                needsUsername = true
                                                usernameCheckComplete = true
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                }

                                if (!usernameCheckComplete || isLoading) {
                                    LoadingScreen()
                                } else if (errorMessage != null) {
                                    ErrorScreen(
                                        errorMessage = errorMessage ?: "Unknown error",
                                        onRetry = {
                                            errorMessage = null
                                            usernameCheckComplete = false
                                        },
                                        onSignOut = {
                                            auth.signOut()
                                            errorMessage = null
                                            usernameCheckComplete = false
                                            needsUsername = false
                                        }
                                    )
                                } else if (needsUsername) {
                                    UsernameScreen(
                                        isLoading = isLoading,
                                        errorMessage = errorMessage,
                                        onSetUsername = { username ->
                                            isLoading = true
                                            errorMessage = null
                                            scope.launch {
                                                try {
                                                    val token = auth.currentUser?.getIdToken(true)?.await()?.token
                                                        ?: throw Exception("No token available")

                                                    val response = ApiClient.usernameService.setUsername(
                                                        authorization = "Bearer $token",
                                                        request = UsernameRequest(username)
                                                    )

                                                    if (response.isSuccessful) {
                                                        needsUsername = false
                                                    } else {
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
                                // This should not happen as we've just signed in, but handling it just in case
                                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .requestIdToken(getString(R.string.default_web_client_id))
                                    .requestEmail()
                                    .build()
                                val googleSignInClient = GoogleSignIn.getClient(this@MainActivity, gso)

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
                                        // TODO: Implement Apple Sign-In
                                        isLoading = false
                                        errorMessage = "Apple Sign-In coming soon"
                                    }
                                )
                            }
                        }
                    }
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
                                    // TODO: Implement Apple Sign-In
                                }
                            )
                        }
                    }
                }
            }
    }
}

@Composable
fun LoadingScreen() {
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
                text = "Sesame",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
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
                text = "Oops!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRetry,
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
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.labelLarge
                )
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

            Spacer(modifier = Modifier.height(32.dp))

            // Username creation prompt
            Text(
                text = "Let's create your username",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This will be how people find and mention you on Sesame.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

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