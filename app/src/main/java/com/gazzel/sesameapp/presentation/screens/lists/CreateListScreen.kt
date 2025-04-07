package com.gazzel.sesameapp.presentation.screens.lists

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
fun CreateListScreen(
    navController: NavController,
    viewModel: CreateListViewModel = hiltViewModel()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New List") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) { // Use navigateUp
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
                label = { Text("List Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isPublic,
                    onCheckedChange = { isPublic = it }
                )
                Text("Make this list public")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    viewModel.createList(
                        title = title,
                        description = description,
                        isPublic = isPublic
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && !(uiState is CreateListUiState.Loading)
            ) {
                if (uiState is CreateListUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Create List")
                }
            }

            if (uiState is CreateListUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (uiState as CreateListUiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is CreateListUiState.Success) {
            // **IMPORTANT**: You need the ID of the newly created list here.
            // The CreateListUiState.Success should ideally contain the new list or its ID.
            // Let's assume the ViewModel updates the state with the ID upon success.
            // We'll modify the CreateListViewModel and State for this.

            val newListId = (uiState as CreateListUiState.Success).newListId // Assume this exists now
            if (newListId != null) {
                // Navigate to SearchPlaces, passing the new list ID
                navController.navigate(Screen.SearchPlaces.createRoute(newListId)) {
                    // Optional: pop CreateListScreen off the back stack
                    popUpTo(Screen.CreateList.route) { inclusive = true }
                }
            } else {
                // Handle case where ID is missing after creation (error)
                Log.e("CreateListScreen", "List created but ID is missing.")
                navController.popBackStack() // Go back anyway
            }
            // Reset VM state if needed viewModel.resetState()
        }
    }
} 