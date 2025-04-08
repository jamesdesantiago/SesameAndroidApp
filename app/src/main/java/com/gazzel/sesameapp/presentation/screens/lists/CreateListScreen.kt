// app/src/main/java/com/gazzel/sesameapp/presentation/screens/lists/CreateListScreen.kt
// (Content moved from CreateListActivity and updated)
package com.gazzel.sesameapp.presentation.screens.lists

import android.util.Log
import androidx.compose.foundation.layout.* // Use wildcard import for layout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.* // Use wildcard import for material3
import androidx.compose.runtime.* // Use wildcard import for runtime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gazzel.sesameapp.presentation.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class) // Needed for Scaffold, TopAppBar, etc.
@Composable
fun CreateListScreen( // Renamed function slightly for clarity
    navController: NavController,
    viewModel: CreateListViewModel = hiltViewModel() // Inject ViewModel
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    // Handle navigation automatically on Success state change
    LaunchedEffect(uiState) {
        if (uiState is CreateListUiState.Success) {
            val newListId = (uiState as CreateListUiState.Success).newListId
            if (newListId != null) {
                Log.d("CreateListScreen", "List created with ID: $newListId, navigating to SearchPlaces.")
                // Navigate to SearchPlaces, passing the new list ID
                navController.navigate(Screen.SearchPlaces.createRoute(newListId)) {
                    // Pop CreateListScreen off the back stack so back button goes to ListsScreen/Home
                    popUpTo(Screen.CreateList.route) { inclusive = true }
                }
            } else {
                // Handle case where ID is missing after creation (error)
                Log.e("CreateListScreen", "List created but ID is missing. Navigating back.")
                // Potentially show a Snackbar error here
                navController.popBackStack() // Go back anyway
            }
            viewModel.resetState() // Reset ViewModel state after handling navigation
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New List") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) { // Use navigateUp for back
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                // Add colors if desired using TopAppBarDefaults.topAppBarColors(...)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp),       // Apply screen-specific padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("List Title*") }, // Indicate required field
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = uiState !is CreateListUiState.Loading // Disable when loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = uiState !is CreateListUiState.Loading // Disable when loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // Make row clickable to toggle checkbox as well
                // modifier = Modifier.clickable { if (uiState !is CreateListUiState.Loading) isPublic = !isPublic }
            ) {
                Checkbox(
                    checked = isPublic,
                    onCheckedChange = { if (uiState !is CreateListUiState.Loading) isPublic = it },
                    enabled = uiState !is CreateListUiState.Loading // Disable when loading
                )
                // Make text clickable too
                Text(
                    "Make this list public",
                    modifier = Modifier.clickable(enabled = uiState !is CreateListUiState.Loading) { isPublic = !isPublic }.padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Button and Loading/Error Handling ---
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val currentState = uiState) {
                    is CreateListUiState.Loading -> {
                        CircularProgressIndicator()
                    }
                    is CreateListUiState.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button( // Show button even on error to allow retry
                                onClick = { viewModel.createList(title, description, isPublic) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = title.isNotBlank() // Basic validation
                            ) {
                                Text("Retry Create List")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is CreateListUiState.Initial, is CreateListUiState.Success -> { // Show button in Initial state
                        Button(
                            onClick = { viewModel.createList(title, description, isPublic) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = title.isNotBlank() // Enable only if title is not blank
                        ) {
                            Text("Create & Add Places")
                        }
                    }
                }
            }
        } // End Column
    } // End Scaffold
}