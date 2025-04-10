package com.gazzel.sesameapp.presentation.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Use wildcard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person // Keep
import androidx.compose.material3.* // Use wildcard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Import
import androidx.compose.ui.text.style.TextAlign // Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Local state for text fields - initialized when Success state is first observed
    var displayName by remember { mutableStateOf("") }
    var profilePictureUrl by remember { mutableStateOf("") } // Keep this if you plan image picking

    var showImagePicker by remember { mutableStateOf(false) }
    var saveAttempted by remember { mutableStateOf(false) } // Track if save was clicked

    // Update local state when the user data loads from the ViewModel
    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Success) {
            val user = (uiState as ProfileUiState.Success).user
            // Only set initial value if not already set by user input
            if (displayName.isEmpty() && user.displayName != null) {
                displayName = user.displayName ?: ""
            }
            if (profilePictureUrl.isEmpty() && user.profilePicture != null) {
                profilePictureUrl = user.profilePicture ?: ""
            }

            // If save was attempted and now we are back to success, navigate back
            if(saveAttempted){
                navController.navigateUp()
                saveAttempted = false // Reset flag
            }
        }
    }

    // Show Snackbar on Error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.Error) {
            val errorState = uiState as ProfileUiState.Error
            snackbarHostState.showSnackbar(
                message = errorState.message, // Show specific error
                duration = SnackbarDuration.Short
            )
            saveAttempted = false // Reset flag on error
            // Consider resetting to previous state? VM currently stays in Error until next action.
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }, // Add SnackbarHost
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile_title)) }, // <<< Use String Res
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button)) // <<< Use String Res
                    }
                }
            )
        }
    ) { paddingValues ->
        // Show loading overlay if saving
        val isLoading = uiState is ProfileUiState.Loading

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile picture placeholder/Image
                // TODO: Replace Icon with Coil Image loading if profilePictureUrl is implemented
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clickable(enabled = !isLoading) { showImagePicker = true } // Disable click when loading
                        .align(Alignment.CenterHorizontally) // Center the box itself
                    // Add background/border if needed
                    ,
                    contentAlignment = Alignment.Center
                ){
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.cd_profile_picture_placeholder), // <<< Use String Res
                        modifier = Modifier.size(120.dp), // Icon fills the Box
                        tint = MaterialTheme.colorScheme.onSurfaceVariant // Placeholder tint
                    )
                    // Overlay a small edit icon if desired
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit_profile_picture), // <<< Use String Res
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }


                Spacer(modifier = Modifier.height(32.dp))

                // Display name
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.label_display_name)) }, // <<< Use String Res
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading // Disable when loading
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Save button
                Button(
                    onClick = {
                        saveAttempted = true // Mark that save was clicked
                        viewModel.updateProfile(
                            // Send null if blank, otherwise send the value
                            displayName = displayName.trim().takeIf { it.isNotEmpty() },
                            profilePicture = profilePictureUrl.trim().takeIf { it.isNotEmpty() } // Assuming URL for now
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading // Disable when loading
                ) {
                    // Show indicator inside button when loading
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary, // Ensure contrast
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.button_save_changes)) // <<< Use String Res
                    }
                }

                // Error message is now handled by Snackbar

            } // End Column
        } // End Box
    } // End Scaffold

    // Image picker dialog (Content uses string resources)
    if (showImagePicker) {
        AlertDialog(
            onDismissRequest = { showImagePicker = false },
            title = { Text(stringResource(R.string.dialog_update_picture_title)) }, // <<< Use String Res
            text = { Text(stringResource(R.string.dialog_update_picture_text)) }, // <<< Use String Res
            confirmButton = {
                TextButton(
                    onClick = {
                        // TODO: Implement image picking logic (e.g., launch gallery/camera intent)
                        // On result, update the profilePictureUrl state variable and maybe call VM immediately or wait for Save button
                        showImagePicker = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_update_picture_confirm_button)) // <<< Use String Res
                }
            },
            dismissButton = {
                TextButton(onClick = { showImagePicker = false }) {
                    Text(stringResource(R.string.dialog_button_cancel)) // <<< Use String Res (Reused)
                }
            }
        )
    }
}