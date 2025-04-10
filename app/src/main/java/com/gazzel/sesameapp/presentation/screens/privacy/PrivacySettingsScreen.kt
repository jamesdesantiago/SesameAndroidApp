package com.gazzel.sesameapp.presentation.screens.privacy

import androidx.compose.foundation.layout.* // Wildcard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error // For Error state
import androidx.compose.material3.* // Wildcard
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // Import
import androidx.compose.ui.text.style.TextAlign // Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // Import R
import com.gazzel.sesameapp.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    navController: NavController,
    viewModel: PrivacySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Handle navigation when account is deleted
    LaunchedEffect(uiState) {
        if (uiState is PrivacySettingsUiState.AccountDeleted) {
            // Navigate to Login screen and clear back stack up to Home
            navController.navigate(Screen.Login.route) {
                popUpTo(Screen.Home.route) { inclusive = true } // Adjust popUpTo if needed
            }
            // Reset VM state if necessary after navigation (though usually not needed if screen is gone)
            // viewModel.resetStateAfterDeletion()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.privacy_settings_title)) }, // <<< Use String Res
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button)) // <<< Use String Res
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) { // Use variable
            is PrivacySettingsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is PrivacySettingsUiState.Success -> {
                val settings = state.settings

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile visibility Card
                    PrivacySettingCard(
                        title = stringResource(R.string.privacy_setting_profile_title), // <<< Use String Res
                        description = stringResource(R.string.privacy_setting_profile_desc), // <<< Use String Res
                        settingLabel = stringResource(R.string.privacy_setting_profile_label), // <<< Use String Res
                        isChecked = settings.profileIsPublic,
                        onCheckedChange = { viewModel.updateProfileVisibility(it) }
                    )

                    // List visibility Card
                    PrivacySettingCard(
                        title = stringResource(R.string.privacy_setting_lists_title), // <<< Use String Res
                        description = stringResource(R.string.privacy_setting_lists_desc), // <<< Use String Res
                        settingLabel = stringResource(R.string.privacy_setting_lists_label), // <<< Use String Res
                        isChecked = settings.listsArePublic,
                        onCheckedChange = { viewModel.updateListVisibility(it) }
                    )

                    // Data collection Card
                    PrivacySettingCard(
                        title = stringResource(R.string.privacy_setting_data_title), // <<< Use String Res
                        description = stringResource(R.string.privacy_setting_data_desc), // <<< Use String Res
                        settingLabel = stringResource(R.string.privacy_setting_data_label), // <<< Use String Res
                        isChecked = settings.allowAnalytics,
                        onCheckedChange = { viewModel.updateAnalytics(it) }
                    )

                    Spacer(modifier = Modifier.weight(1f)) // Push button to bottom

                    // Delete account button
                    Button(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth(), // Make button full width
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError // Ensure contrast
                        )
                    ) {
                        Text(stringResource(R.string.privacy_button_delete_account)) // <<< Use String Res
                    }
                }
            }
            is PrivacySettingsUiState.Error -> {
                // Use Column with Retry for errors
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ){
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.message, // Keep specific error
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadPrivacySettings() }) { // Retry loading settings
                        Text(stringResource(R.string.button_retry)) // <<< Use String Res
                    }
                }
            }
            is PrivacySettingsUiState.AccountDeleted -> {
                // Screen is about to navigate away via LaunchedEffect
                // Optionally show a brief "Account Deleted" message
                Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.privacy_account_deleted_message))
                }
            }
        }
    }

    // Delete account confirmation dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, // Add warning icon
            title = { Text(stringResource(R.string.dialog_delete_account_title)) }, // <<< Use String Res
            text = { Text(stringResource(R.string.dialog_delete_account_text)) }, // <<< Use String Res
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false // Dismiss dialog first
                        viewModel.deleteAccount() // Trigger VM action (which will change state for LaunchedEffect)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.dialog_delete_confirm_button)) // <<< Use String Res
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text(stringResource(R.string.dialog_delete_cancel_button)) // <<< Use String Res
                }
            }
        )
    }
}

// Extracted Reusable Card Composable for settings
@Composable
private fun PrivacySettingCard(
    title: String,
    description: String,
    settingLabel: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(settingLabel, modifier = Modifier.weight(1f)) // Allow text to wrap if needed
                Switch(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }
}