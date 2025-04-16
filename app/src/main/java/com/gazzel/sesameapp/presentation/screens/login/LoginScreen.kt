package com.gazzel.sesameapp.presentation.screens.login // Ensure correct package

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.* // Use wildcard imports
import androidx.compose.material3.* // Use wildcard imports
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource // <<< Import stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // <<< Import R class
import com.gazzel.sesameapp.presentation.navigation.Screen


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding) // Apply scaffold padding
                .padding(16.dp),  // Apply screen padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo and welcome text
            Text(
                // text = "Welcome to Sesame", // Before
                text = stringResource(R.string.login_welcome_title), // After
                style = MaterialTheme.typography.headlineLarge,
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

            // Google Sign-In button
            Button(
                onClick = { launcher.launch(viewModel.getGoogleSignInIntent()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    // Consider using a color that contrasts well with the text/icon
                    containerColor = MaterialTheme.colorScheme.surfaceVariant // Or Color.White if preferred
                ),
                enabled = uiState !is LoginUiState.Loading // Disable button while loading
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google),
                        // contentDescription = "Google Sign In", // Before
                        contentDescription = stringResource(R.string.cd_login_google_icon), // After
                        modifier = Modifier.size(24.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.login_button_google)) // After
                }
            }

            // Loading indicator and Error message display
            Spacer(modifier = Modifier.height(16.dp))
            when (val currentState = uiState) {
                is LoginUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.state_loading)) // Display loading text
                }
                is LoginUiState.Error -> {
                    Text(
                        // Use the specific message from the ViewModel state for more context
                        text = currentState.message,
                        // Fallback to a generic message if needed:
                        // text = stringResource(R.string.error_login_failed),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp) // Add padding for readability
                    )
                }
                else -> {
                    // No indicator or error shown in Initial or Success state here
                    // Add a placeholder Spacer to maintain layout consistency if desired
                    Spacer(modifier = Modifier.height(48.dp)) // Match approx height of indicator+text
                }
            }
        }
    }
}