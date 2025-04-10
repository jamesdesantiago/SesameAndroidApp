package com.gazzel.sesameapp.presentation.screens.auth // Ensure package is correct

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.* // Wildcard import
import androidx.compose.material3.* // Wildcard import
import androidx.compose.material.icons.Icons // Import Icons
import androidx.compose.material.icons.filled.Error // Import Error icon for example
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext // Keep if needed for other reasons
import androidx.compose.ui.res.painterResource // Import painterResource
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // <<< Import R
import com.gazzel.sesameapp.presentation.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class) // Needed for Scaffold
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    // context not explicitly needed here anymore
    // val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                try {
                    val idToken = viewModel.handleSignInResult(result.data)
                    viewModel.signInWithGoogle(idToken)
                } catch (e: Exception) {
                    // ViewModel should ideally expose this error via its state
                    // For now, just logging or showing a generic snackbar could work
                    Log.e("LoginScreen", "handleSignInResult failed", e)
                    // You could potentially set an error state in the VM here if needed
                    // viewModel.setSignInError("Failed to process sign-in result.")
                }
            }
        } else {
            Log.w("LoginScreen", "Google Sign-In intent failed or was cancelled. Result code: ${result.resultCode}")
            // Optionally show a message to the user if the sign-in was cancelled
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Success) {
            // Check for username setup requirement *before* navigating home
            // This logic might move to a MainViewModel or App-level coordinator later
            Log.d("LoginScreen", "Auth Success, navigating to Home.") // Changed nav destination
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
            // TODO: Add check for username setup and navigate to Screen.UsernameSetup if needed
        }
    }

    // Use Scaffold for consistent structure and potential Snackbars later
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // Apply Scaffold padding
                .padding(16.dp), // Apply screen padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // TODO: Consider adding an App Icon/Logo here

            Text(
                // text = "Welcome to Sesame", // Before
                text = stringResource(R.string.login_welcome_title), // After
                style = MaterialTheme.typography.headlineMedium, // Adjusted style
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                // text = "Your personal list companion", // Before
                text = stringResource(R.string.login_welcome_subtitle), // After
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Button and Status Display Area
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                when (val currentState = authState) { // Use variable
                    is AuthState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally){
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.state_loading))
                        }
                    }
                    is AuthState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally){
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentState.message, // Show specific error from VM
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // Show button again to allow retry
                            GoogleSignInButton(
                                isLoading = false, // Not loading during error display
                                onClick = {
                                    scope.launch {
                                        try {
                                            val intentSender = viewModel.getSignInIntent()
                                            launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                                        } catch (e: Exception) {
                                            Log.e("LoginScreen", "getSignInIntent failed", e)
                                            // viewModel.setSignInError("Could not start sign-in process.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                    is AuthState.Initial, is AuthState.Success -> { // Show button in Initial (Success handles nav)
                        GoogleSignInButton(
                            isLoading = false,
                            onClick = {
                                scope.launch {
                                    try {
                                        val intentSender = viewModel.getSignInIntent()
                                        launcher.launch(IntentSenderRequest.Builder(intentSender).build())
                                    } catch (e: Exception) {
                                        Log.e("LoginScreen", "getSignInIntent failed", e)
                                        // viewModel.setSignInError("Could not start sign-in process.")
                                    }
                                }
                            }
                        )
                    }
                }
            } // End Box
        } // End Column
    } // End Scaffold
}

// Extracted Google Sign In Button
@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
){
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = !isLoading, // Disable if loading
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface // White/Surface for Google button
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_google),
                contentDescription = stringResource(R.string.cd_login_google_icon),
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.login_button_google),
                // Explicitly set text color for contrast on surface
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}