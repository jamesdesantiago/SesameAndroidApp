// app/src/main/java/com/gazzel/sesameapp/presentation/screens/lists/CreateListScreen.kt
package com.gazzel.sesameapp.presentation.screens.lists

import android.util.Log
import androidx.compose.foundation.clickable // <<< Keep clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource // <<< Import
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.R // <<< Import R
import com.gazzel.sesameapp.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListScreen(
    navController: NavController,
    viewModel: CreateListViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf<String?>(null) } // For local validation message

    val uiState by viewModel.uiState.collectAsState()

    // Basic validation function
    fun validateInput(): Boolean {
        if (title.isBlank()) {
            titleError = R.string.error_title_blank // Set resource ID
            return false
        }
        titleError = null // Clear error if valid
        return true
    }

    LaunchedEffect(uiState) {
        if (uiState is CreateListUiState.Success) {
            val newListId = (uiState as CreateListUiState.Success).newListId
            if (newListId != null) {
                Log.d("CreateListScreen", "List created with ID: $newListId, navigating to SearchPlaces.")
                navController.navigate(Screen.SearchPlaces.createRoute(newListId)) {
                    popUpTo(Screen.CreateList.route) { inclusive = true }
                }
            } else {
                Log.e("CreateListScreen", "List created but ID is missing. Navigating back.")
                // TODO: Show Snackbar error here using a separate event from VM if needed
                navController.popBackStack()
            }
            viewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_list_title)) }, // <<< Use String Res
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back_button)) // <<< Use String Res
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
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (titleError != null && it.isNotBlank()) {
                        titleError = null // Clear error on typing
                    }
                },
                label = { Text(stringResource(R.string.label_list_title_required)) }, // <<< Use String Res
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = uiState !is CreateListUiState.Loading,
                isError = titleError != null || uiState is CreateListUiState.Error, // Show error state
                supportingText = { // Display validation error below field
                    if (titleError != null) {
                        Text(
                            stringResource(titleError!!),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.label_list_description_optional)) }, // <<< Use String Res
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = uiState !is CreateListUiState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isLoading = uiState is CreateListUiState.Loading
                Checkbox(
                    checked = isPublic,
                    onCheckedChange = { if (!isLoading) isPublic = it }, // Check loading state
                    enabled = !isLoading // Disable when loading
                )
                Text(
                    text = stringResource(R.string.checkbox_make_public), // <<< Use String Res
                    modifier = Modifier
                        .clickable(enabled = !isLoading) { isPublic = !isPublic } // Check loading state
                        .padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Button and Loading/Error Handling ---
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val currentState = uiState) {
                    is CreateListUiState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.state_loading))
                    }
                    is CreateListUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(
                                onClick = {
                                    if (validateInput()) { // Validate before retrying
                                        viewModel.createList(title, description, isPublic)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true // Always allow retry
                            ) {
                                Text(stringResource(R.string.button_retry_create_list)) // <<< Use String Res
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentState.message, // Show error from VM
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is CreateListUiState.Initial, is CreateListUiState.Success -> { // Show button in Initial state
                        Button(
                            onClick = {
                                if (validateInput()) { // Validate before creating
                                    viewModel.createList(title, description, isPublic)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = true // Validation check happens onClick
                        ) {
                            Text(stringResource(R.string.button_create_and_add)) // <<< Use String Res
                        }
                    }
                }
            }
        } // End Column
    } // End Scaffold
}