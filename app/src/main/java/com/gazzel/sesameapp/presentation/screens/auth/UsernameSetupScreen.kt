package com.gazzel.sesameapp.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.presentation.navigation.Screen

@Composable
fun UsernameSetupScreen(
    navController: NavController,
    viewModel: MainViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (uiState) {
            is MainUiState.Ready -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.UsernameSetup.route) { inclusive = true }
                }
            }
            else -> {}
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
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is MainUiState.Loading
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState) {
            is MainUiState.Loading -> {
                CircularProgressIndicator()
            }
            is MainUiState.Error -> {
                Text(
                    text = (uiState as MainUiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Button(
                    onClick = { viewModel.updateUsername(username) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank()
                ) {
                    Text("Continue")
                }
            }
        }
    }
} 