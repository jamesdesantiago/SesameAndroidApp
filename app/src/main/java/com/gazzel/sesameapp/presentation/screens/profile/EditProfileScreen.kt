package com.gazzel.sesameapp.presentation.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var displayName by remember { mutableStateOf("") }
    var profilePictureUrl by remember { mutableStateOf("") }
    var showImagePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val user = (uiState as ProfileUiState.Success).user
            displayName = user.displayName ?: ""
            profilePictureUrl = user.profilePicture ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile picture
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile picture",
                modifier = Modifier
                    .size(120.dp)
                    .clickable { showImagePicker = true }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Display name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    viewModel.updateProfile(
                        displayName = displayName.takeIf { it.isNotBlank() },
                        profilePicture = profilePictureUrl.takeIf { it.isNotBlank() }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !(uiState is ProfileUiState.Loading)
            ) {
                if (uiState is ProfileUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Changes")
                }
            }

            if (uiState is ProfileUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (uiState as ProfileUiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Image picker dialog
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text("Update Profile Picture") },
            text = { Text("Choose a new profile picture") },
            confirmButton = {
                TextButton(
                    onClick = {
                        // TODO: Implement image picking
                        showImagePicker = false
                    }
                ) {
                    Text("Choose Image")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
} 