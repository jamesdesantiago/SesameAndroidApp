package com.gazzel.sesameapp.presentation.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.* // Use wildcard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.* // Use wildcard
import androidx.compose.material3.* // Use wildcard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector // Import
import androidx.compose.ui.res.stringResource // Import
import androidx.compose.ui.text.style.TextAlign // Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R
import com.gazzel.sesameapp.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Handle navigation away when SignedOut state is reached
    LaunchedEffect(uiState) {
        if (uiState is ProfileUiState.SignedOut) {
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Home.route) { inclusive = true } // Pop back stack to Home, then Login replaces it
            }
            // Don't need to reset VM state as the screen is being left
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) }, // <<< Use String Res
                navigationIcon = {
                    // Conditionally show back arrow if this screen isn't a top-level destination
                    // If it IS a top-level destination (like from a bottom bar), you might hide it
                    // or change its behavior. Assuming it can be navigated back from for now.
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button)) // <<< Use String Res
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) { // Use variable
            is ProfileUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ProfileUiState.Success -> {
                val user = state.user

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile header - TODO: Replace with actual Image loading
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.cd_profile_picture), // <<< Use String Res
                        modifier = Modifier.size(120.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = user.displayName ?: user.username ?: stringResource(R.string.default_username), // <<< Use String Res for default
                        style = MaterialTheme.typography.headlineMedium
                    )

                    // Show email only if available
                    if (user.email.isNotBlank()) {
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Profile actions using reusable composable
                    ProfileActionButton(
                        icon = Icons.Default.Edit,
                        labelResId = R.string.profile_action_edit, // <<< Pass Res ID
                        onClick = { navController.navigate(Screen.EditProfile.route) }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp)) // Add divider
                    ProfileActionButton(
                        icon = Icons.Default.Notifications,
                        labelResId = R.string.profile_action_notifications, // <<< Pass Res ID
                        onClick = { navController.navigate(Screen.Notifications.route) }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileActionButton(
                        icon = Icons.Default.Lock,
                        labelResId = R.string.profile_action_privacy, // <<< Pass Res ID
                        onClick = { navController.navigate(Screen.PrivacySettings.route) }
                    )
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    ProfileActionButton(
                        icon = Icons.Default.Info,
                        labelResId = R.string.profile_action_help, // <<< Pass Res ID
                        onClick = { navController.navigate(Screen.Help.route) }
                    )

                    Spacer(modifier = Modifier.weight(1f)) // Push sign out to bottom

                    // Sign out button
                    Button(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(), // Make full width
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError // Ensure text contrast
                        )
                    ) {
                        Text(stringResource(R.string.profile_button_sign_out)) // <<< Use String Res
                    }
                }
            }
            is ProfileUiState.Error -> {
                Column( // Wrap Error in a Column for Button
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ){
                    Icon(Icons.Filled.Error, contentDescription=null, tint=MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message, // Keep specific error
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    // Add retry button to reload profile
                    Button(onClick = { viewModel.loadUserProfile() }) {
                        Text(stringResource(R.string.button_retry))
                    }
                }
            }
            is ProfileUiState.SignedOut -> {
                // Handled by LaunchedEffect, show placeholder briefly if needed
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.signing_out))
                }
            }
        }
    }

    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.Filled.Logout, contentDescription = null) }, // Add icon
            title = { Text(stringResource(R.string.dialog_sign_out_title)) }, // <<< Use String Res
            text = { Text(stringResource(R.string.dialog_sign_out_text)) }, // <<< Use String Res
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false // Dismiss first
                        viewModel.signOut() // Trigger VM action (nav handled by LaunchedEffect)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.profile_button_sign_out)) // <<< Use String Res
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.dialog_button_cancel)) // <<< Use String Res
                }
            }
        )
    }
}

// Updated ProfileActionButton to use String Resource ID
@OptIn(ExperimentalMaterial3Api::class) // Needed for ListItem
@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    labelResId: Int, // Pass Resource ID
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(labelResId)) }, // <<< Use String Res
        leadingContent = { Icon(icon, contentDescription = null) }, // Label describes it
        modifier = Modifier.clickable(onClick = onClick)
            .fillMaxWidth() // Ensure full clickable width
    )
}