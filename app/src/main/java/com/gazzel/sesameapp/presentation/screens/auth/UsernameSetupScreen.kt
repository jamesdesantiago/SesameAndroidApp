package com.gazzel.sesameapp.presentation.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.presentation.navigation.Screen

@Composable
fun UsernameSetupScreen(
    navController: NavController,
    // Use the new ViewModel
    viewModel: UsernameSetupViewModel = hiltViewModel() // <-- Change ViewModel type
) {
    var username by remember { mutableStateOf("") }
    // Collect the correct state type
    val uiState by viewModel.uiState.collectAsState() // <-- uiState is now UsernameSetupState

    // Handle navigation on Success state
    LaunchedEffect(uiState) {
        when (uiState) {
            // Check for the new Success state
            is UsernameSetupState.Success -> { // <-- Change State check
                navController.navigate(Screen.Home.route) { // Navigate to Home on success
                    popUpTo(Screen.UsernameSetup.route) { inclusive = true }
                }
            }
            else -> { /* Handle other states if needed, e.g., show snackbar on error */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose a Username",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { newUsername ->
                // Optional: Basic input filtering if desired
                // username = newUsername.filter { it.isLetterOrDigit() }
                username = newUsername
            },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            // Check against the new Loading state
            enabled = uiState !is UsernameSetupState.Loading, // <-- Change State check
            isError = uiState is UsernameSetupState.Error // Optionally highlight field on error
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Handle the new states
        when (val currentState = uiState) { // Assign to currentState for easier access
            is UsernameSetupState.Loading -> { // <-- Change State check
                CircularProgressIndicator()
            }
            is UsernameSetupState.Error -> { // <-- Change State check
                Text(
                    // Access message from the correct state object
                    text = currentState.message, // <-- Access message field
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp) // Add some padding
                )
                // Add the button back even on error state so user can retry
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.updateUsername(username) }, // <-- Call correct VM function
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank() && username.length >= 3 // Basic validation
                ) {
                    Text("Retry") // Change text maybe?
                }

            }
            // Handle Idle and Success (button shown below for Idle)
            is UsernameSetupState.Idle -> {
                Button(
                    onClick = { viewModel.updateUsername(username) }, // <-- Call correct VM function
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank() && username.length >= 3 // Basic validation
                ) {
                    Text("Continue")
                }
            }
            is UsernameSetupState.Success -> {
                // Optionally show a temporary success indicator or just rely on navigation
                Text("Username set successfully!") // Or just let navigation handle it
            }
        }
    }
}